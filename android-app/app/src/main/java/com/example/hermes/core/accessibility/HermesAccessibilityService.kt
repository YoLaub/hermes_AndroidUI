package com.example.hermes.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.hermes.core.mobilecontrol.MobileScreenData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

class HermesAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HermesAccessService"
        
        private var instance: HermesAccessibilityService? = null

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _currentForegroundPackage = MutableStateFlow("")
        val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

        fun getInstance(): HermesAccessibilityService? = instance
        fun isRunning(): Boolean = instance != null && _isServiceActive.value
    }

    private val nodeHelper = AccessibilityNodeHelper()

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

    fun scroll(direction: String): Boolean {
        return nodeHelper.scroll(rootInActiveWindow, direction)
    }

    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
