package com.example.hermes.core.accessibility

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImageSize(val width: Int, val height: Int)

data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Pure geometry for screenshots: no Android types, so it is unit-tested on the JVM. */
object ScreenshotGeometry {

    /** Size after downscaling so the long side is at most [maxLongSide]; never upscales. Null for degenerate input. */
    fun targetSize(width: Int, height: Int, maxLongSide: Int): ImageSize? {
        if (width <= 0 || height <= 0 || maxLongSide <= 0) return null
        val longSide = max(width, height)
        if (longSide <= maxLongSide) return ImageSize(width, height)
        val scale = maxLongSide.toDouble() / longSide
        return ImageSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
    }

    /**
     * A pixel of the (scaled) screenshot back to a pixel of the screen; null outside the image.
     * The capture is of one window whose size is [windowWidth] x [windowHeight] and whose top-left
     * corner is at ([offsetX], [offsetY]) on the screen (0, 0 for a full-screen window).
     */
    fun toScreenPoint(
        x: Int, y: Int, image: ImageSize, windowWidth: Int, windowHeight: Int,
        offsetX: Int = 0, offsetY: Int = 0
    ): TapPoint? {
        if (image.width <= 0 || image.height <= 0 || windowWidth <= 0 || windowHeight <= 0) return null
        if (x < 0 || y < 0 || x >= image.width || y >= image.height) return null
        return TapPoint(
            offsetX + ((x.toLong() * windowWidth) / image.width).toInt(),
            offsetY + ((y.toLong() * windowHeight) / image.height).toInt()
        )
    }
}

/** Password fields are blacked out of the image before it leaves the phone. */
object Redaction {

    /**
     * [screenRects] (screen pixels) mapped into the captured [window] and then to the scaled image, grown by
     * [margin] pixels, clamped to the image. Empty rectangles, and those that fall outside the window, are dropped.
     */
    fun imageRects(
        screenRects: List<ScreenRect>,
        window: ScreenRect,
        image: ImageSize,
        margin: Int
    ): List<ScreenRect> {
        val windowWidth = window.right - window.left
        val windowHeight = window.bottom - window.top
        if (windowWidth <= 0 || windowHeight <= 0 || image.width <= 0 || image.height <= 0) return emptyList()
        val sx = image.width.toDouble() / windowWidth
        val sy = image.height.toDouble() / windowHeight
        return screenRects.mapNotNull { r ->
            if (r.right <= r.left || r.bottom <= r.top) return@mapNotNull null
            val left = max(0, floor((r.left - window.left) * sx).toInt() - margin)
            val top = max(0, floor((r.top - window.top) * sy).toInt() - margin)
            val right = min(image.width, ceil((r.right - window.left) * sx).toInt() + margin)
            val bottom = min(image.height, ceil((r.bottom - window.top) * sy).toInt() + margin)
            if (right <= left || bottom <= top) null else ScreenRect(left, top, right, bottom)
        }
    }
}

class Encoded(val quality: Int, val bytes: ByteArray)

/** Keeps the payload within what the relay accepts, trading quality for size. */
object JpegBudget {
    val QUALITIES = listOf(70, 50, 35)

    /**
     * Encodes at each quality in turn and keeps only the first result that fits in [maxBytes] (the failed
     * attempts are dropped as soon as they are measured), or null if none fits.
     */
    fun firstThatFits(maxBytes: Int, qualities: List<Int> = QUALITIES, encode: (Int) -> ByteArray): Encoded? {
        for (q in qualities) {
            val bytes = encode(q)
            if (bytes.size <= maxBytes) return Encoded(q, bytes)
        }
        return null
    }
}

object ScreenshotErrors {
    /**
     * Captures need Android 14 (API 34): `takeScreenshotOfWindow` is the only way to capture the target app's
     * window alone. Before that only the whole display could be captured, which would include other apps'
     * notifications and overlays, outside what the user consented to.
     */
    fun isSupported(sdkInt: Int): Boolean = sdkInt >= 34

    /**
     * Maps AccessibilityService.ERROR_TAKE_SCREENSHOT_* to our codes (literals, to stay JVM-testable):
     * 2 NO_ACCESSIBILITY_ACCESS, 3 INTERVAL_TIME_SHORT, 6 SECURE_WINDOW; the rest is a plain failure.
     */
    fun toCode(androidErrorCode: Int): String = when (androidErrorCode) {
        2 -> "ACCESSIBILITY_DISABLED"
        3 -> "SCREENSHOT_TOO_FAST"
        6 -> "SCREENSHOT_BLOCKED_SECURE_WINDOW"
        else -> "ACTION_FAILED"
    }
}

/**
 * What the last screenshot looked like, so a tap by coordinates is only valid for that very capture:
 * same revision, the captured window's position, and not older than [MAX_AGE_MS] (the screen can change
 * without anything observing it).
 */
data class ScreenshotContext(
    val revision: String,
    val image: ImageSize,
    val window: ScreenRect,
    val capturedAtMs: Long
) {
    fun toScreenPoint(expectedRevision: String?, x: Int, y: Int, nowMs: Long): TapPoint? {
        if (expectedRevision == null || expectedRevision != revision) return null
        val age = nowMs - capturedAtMs
        if (age < 0 || age > MAX_AGE_MS) return null
        return ScreenshotGeometry.toScreenPoint(
            x, y, image, window.right - window.left, window.bottom - window.top, window.left, window.top
        )
    }

    companion object {
        const val MAX_AGE_MS = 15_000L
    }
}
