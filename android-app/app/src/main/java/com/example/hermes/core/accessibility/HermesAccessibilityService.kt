package com.example.hermes.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.hermes.core.mobilecontrol.MobileScreenData
import com.example.hermes.core.mobilecontrol.MobileScreenshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

class HermesAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HermesAccessService"
        private const val SCREENSHOT_MAX_LONG_SIDE = 1280
        // The relay accepts at most 1,000,000 decoded bytes; keep a margin.
        private const val SCREENSHOT_MAX_BYTES = 900_000
        private const val REDACTION_MARGIN_PX = 6
        
        private var instance: HermesAccessibilityService? = null

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _currentForegroundPackage = MutableStateFlow("")
        val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

        fun getInstance(): HermesAccessibilityService? = instance
        fun isRunning(): Boolean = instance != null && _isServiceActive.value
    }

    private val nodeHelper = AccessibilityNodeHelper()

    /** What the last screenshot looked like: a tap by coordinates is only valid for that very capture. */
    @Volatile
    private var screenshotContext: ScreenshotContext? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.i(TAG, "HermesAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Update foreground package tracking
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            _currentForegroundPackage.value = pkg
        }
        // A new window or a scroll makes the last screenshot's coordinates meaningless.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            screenshotContext = null
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "HermesAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isServiceActive.value = false
        instance = null
        nodeHelper.clear()
        Log.i(TAG, "HermesAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isServiceActive.value = false
        instance = null
        nodeHelper.clear()
        super.onDestroy()
        Log.i(TAG, "HermesAccessibilityService destroyed")
    }

    // ── Inspection & Actions ────────────────────────────────────────────────

    fun observeScreen(): MobileScreenData {
        val root = rootInActiveWindow
        val screenData = nodeHelper.extractScreenData(root)
        // If root package is present, update current package
        if (screenData.packageName.isNotBlank()) {
            _currentForegroundPackage.value = screenData.packageName
        }
        return screenData
    }

    fun isScreenRevisionValid(revision: String?): Boolean {
        return nodeHelper.isRevisionValid(revision)
    }

    fun getForegroundPackage(): String {
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        return rootPkg ?: _currentForegroundPackage.value
    }

    fun clickElement(elementRef: String, expectedRevision: String?): Boolean {
        if (!nodeHelper.isRevisionValid(expectedRevision)) {
            return false
        }
        return nodeHelper.clickElement(elementRef)
    }

    fun setText(elementRef: String, text: String, expectedRevision: String?): Boolean {
        if (!nodeHelper.isRevisionValid(expectedRevision)) {
            return false
        }
        return nodeHelper.setText(elementRef, text)
    }

    /**
     * Fallback when an element cannot be clicked through its accessibility action: tap the centre of its
     * visible bounds with a gesture. Same revision check as a normal click, plus: the target app must still be
     * in the foreground, the element must be enabled and visible and is re-read first, and at least half of it
     * must be on screen.
     */
    suspend fun tapElementCenter(elementRef: String, expectedRevision: String?, targetPackage: String): TapOutcome {
        if (!nodeHelper.isRevisionValid(expectedRevision)) return TapOutcome.REFUSED
        // A coordinate tap lands on whatever is on top: re-check the target app right before dispatching.
        if (!TapGuard.targetStillInForeground(rootInActiveWindow?.packageName?.toString(), targetPackage)) {
            Log.w(TAG, "event=tap_refused reason=target_not_in_foreground")
            return TapOutcome.REFUSED
        }
        val bounds = nodeHelper.visibleBoundsOf(elementRef) ?: return TapOutcome.REFUSED
        val metrics = resources.displayMetrics
        val point = BoundsTap.centerOfBounds(bounds, metrics.widthPixels, metrics.heightPixels)
            ?: return TapOutcome.REFUSED

        return dispatchTap(point)
    }

    /** Injects a short tap at [point] and reports what happened. Callers have already run their checks. */
    private suspend fun dispatchTap(point: TapPoint): TapOutcome {
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val done = CompletableDeferred<TapOutcome>()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                done.complete(TapOutcome.TAPPED)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                done.complete(TapOutcome.CANCELLED)
            }
        }, null)
        if (!dispatched) return TapOutcome.REFUSED
        // No answer in time: the gesture is queued and may still run, so the outcome is unknown.
        return withTimeoutOrNull(2_000) { done.await() } ?: TapOutcome.UNKNOWN
    }

    /**
     * Tap a point read on the LAST screenshot (its pixels). Valid only for that capture: same revision,
     * not older than [ScreenshotContext.MAX_AGE_MS], and dropped as soon as the screen changes (a new
     * window, a scroll) or the session changes. Same guards as any tap: the target app must still be in the
     * foreground. The context is spent by the attempt: the agent must capture again before tapping again.
     */
    suspend fun tapAtScreenshotPoint(revision: String?, x: Int, y: Int, targetPackage: String): TapOutcome {
        val context = screenshotContext ?: return TapOutcome.REFUSED
        screenshotContext = null
        if (!nodeHelper.isRevisionValid(revision)) return TapOutcome.REFUSED
        if (!TapGuard.targetStillInForeground(rootInActiveWindow?.packageName?.toString(), targetPackage)) {
            Log.w(TAG, "event=tap_refused reason=target_not_in_foreground")
            return TapOutcome.REFUSED
        }
        val point = context.toScreenPoint(revision, x, y, System.currentTimeMillis()) ?: return TapOutcome.REFUSED
        return dispatchTap(point)
    }

    /** Forget the last screenshot (session started or ended): no tap may rely on it any more. */
    fun clearScreenshotContext() {
        screenshotContext = null
    }

    /**
     * An image of the target app's WINDOW only (never the whole display, which would include other apps'
     * notifications and overlays), downscaled to a JPEG. Password fields are blacked out before encoding,
     * a protected (FLAG_SECURE) window is refused by Android, and the image only ever lives in memory: it
     * is never written to disk and never logged. The caller must already have checked the session's consent.
     *
     * [observe] runs right after the frame has been obtained, so a failed capture leaves the screen
     * revision (and the agent's element references) untouched.
     */
    suspend fun captureScreenshot(targetPackage: String, observe: () -> MobileScreenData): CaptureOutcome {
        if (!ScreenshotErrors.isSupported(Build.VERSION.SDK_INT)) {
            return CaptureOutcome.Failure("SCREENSHOT_UNSUPPORTED", "Les captures d'écran demandent Android 14 ou plus récent.")
        }
        val before = rootInActiveWindow
        if (!TapGuard.targetStillInForeground(before?.packageName?.toString(), targetPackage)) {
            return CaptureOutcome.Failure("APP_NOT_FOREGROUND", "L'application cible n'est pas au premier plan : capture refusée.")
        }
        val windowId = before?.windowId ?: return CaptureOutcome.Failure("APP_NOT_FOREGROUND", "Fenêtre de l'application introuvable.")

        val frame = when (val raw = requestFrame(windowId)) {
            is RawFrame.Failed -> return CaptureOutcome.Failure(
                ScreenshotErrors.toCode(raw.androidErrorCode), "Capture refusée par Android (code ${raw.androidErrorCode})."
            )
            is RawFrame.Frame -> raw
        }

        // Read the screen state AFTER the frame: the password fields and the window position must be the
        // ones of the image, not of a moment before it.
        val after = rootInActiveWindow
        if (!TapGuard.targetStillInForeground(after?.packageName?.toString(), targetPackage) || after?.windowId != windowId) {
            frame.buffer.close()
            return CaptureOutcome.Failure("APP_NOT_FOREGROUND", "L'écran a changé pendant la capture : capture annulée.")
        }
        val windowBounds = Rect().also { after.window?.getBoundsInScreen(it) }
        val passwordRects = nodeHelper.passwordRects(after)
        val screenData = observe()
        val revision = nodeHelper.getCurrentRevision()

        return withContext(Dispatchers.Default) { encode(frame, windowBounds, passwordRects, revision, screenData) }
    }

    private sealed interface RawFrame {
        class Frame(val buffer: HardwareBuffer, val colorSpace: android.graphics.ColorSpace?) : RawFrame
        class Failed(val androidErrorCode: Int) : RawFrame
    }

    private suspend fun requestFrame(windowId: Int): RawFrame {
        val done = CompletableDeferred<RawFrame>()
        takeWindowScreenshot(windowId, done)
        var delivered = false
        try {
            val result = withTimeoutOrNull(5_000) { done.await() }
            if (result != null) {
                delivered = true
                return result
            }
            return RawFrame.Failed(1)
        } finally {
            if (!delivered) {
                // Timeout or cancellation: complete the deferred ourselves so a frame arriving later is closed
                // by the callback; if one arrived at the very last moment, close it here.
                if (!done.complete(RawFrame.Failed(1))) {
                    (done.getCompleted() as? RawFrame.Frame)?.buffer?.close()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun takeWindowScreenshot(windowId: Int, done: CompletableDeferred<RawFrame>) {
        takeScreenshotOfWindow(windowId, ContextCompat.getMainExecutor(this), object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer = result.hardwareBuffer
                // Already completed (timeout, cancellation): nobody will read this frame, so release it.
                if (!done.complete(RawFrame.Frame(buffer, result.colorSpace))) buffer.close()
            }

            override fun onFailure(errorCode: Int) {
                done.complete(RawFrame.Failed(errorCode))
            }
        })
    }

    private fun encode(
        frame: RawFrame.Frame,
        windowBounds: Rect,
        passwordRects: List<ScreenRect>,
        revision: String,
        screenData: MobileScreenData
    ): CaptureOutcome {
        var wrapped: Bitmap? = null
        var software: Bitmap? = null
        var target: Bitmap? = null
        try {
            wrapped = Bitmap.wrapHardwareBuffer(frame.buffer, frame.colorSpace)
                ?: return CaptureOutcome.Failure("ACTION_FAILED", "Capture illisible.")
            software = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                ?: return CaptureOutcome.Failure("ACTION_FAILED", "Capture illisible.")
            val size = ScreenshotGeometry.targetSize(software.width, software.height, SCREENSHOT_MAX_LONG_SIDE)
                ?: return CaptureOutcome.Failure("ACTION_FAILED", "Taille de capture invalide.")
            // The bitmap is the window, pixel for pixel: anchor it at the window's top-left corner.
            val window = ScreenRect(
                windowBounds.left, windowBounds.top,
                windowBounds.left + software.width, windowBounds.top + software.height
            )

            target = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(target)
            canvas.drawBitmap(software, Rect(0, 0, software.width, software.height), Rect(0, 0, size.width, size.height), Paint(Paint.FILTER_BITMAP_FLAG))
            // Black out password fields BEFORE encoding: the pixels never leave the phone.
            val black = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
            Redaction.imageRects(passwordRects, window, size, REDACTION_MARGIN_PX).forEach {
                canvas.drawRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat(), black)
            }

            val encoded = JpegBudget.firstThatFits(SCREENSHOT_MAX_BYTES) { quality ->
                ByteArrayOutputStream().also { target.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            } ?: return CaptureOutcome.Failure("SCREENSHOT_TOO_LARGE", "La capture dépasse la taille maximale même à la qualité la plus basse.")

            screenshotContext = ScreenshotContext(revision, size, window, System.currentTimeMillis())
            val data = android.util.Base64.encodeToString(encoded.bytes, android.util.Base64.NO_WRAP)
            return CaptureOutcome.Success(MobileScreenshot("image/jpeg", size.width, size.height, data), screenData)
        } finally {
            frame.buffer.close()
            target?.recycle()
            software?.recycle()
            wrapped?.recycle()
        }
    }

    fun scroll(direction: String): Boolean {
        return nodeHelper.scroll(rootInActiveWindow, direction)
    }

    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
