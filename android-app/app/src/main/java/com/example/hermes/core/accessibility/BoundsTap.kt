package com.example.hermes.core.accessibility

data class TapPoint(val x: Int, val y: Int)

/**
 * Where to tap when an element cannot be clicked through its accessibility action
 * (custom views, canvases): the centre of the part of its rectangle that is on screen.
 *
 * Pure and strict on purpose: a malformed or empty rectangle, or an element less than half visible
 * (its tap target is probably elsewhere), yields null and the caller reports the click as failed
 * instead of tapping somewhere unrelated.
 */
object BoundsTap {

    private val FORMAT = Regex("""^\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]$""")

    /** [bounds] uses the format `observe` reports: "[left,top][right,bottom]". */
    fun centerOfBounds(bounds: String, screenWidth: Int, screenHeight: Int): TapPoint? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val m = FORMAT.matchEntire(bounds.trim()) ?: return null
        val values = m.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
        val (left, top, right, bottom) = values
        if (right <= left || bottom <= top) return null

        // Long arithmetic throughout: hostile values must not overflow.
        val visibleLeft = maxOf(left, 0L)
        val visibleTop = maxOf(top, 0L)
        val visibleRight = minOf(right, screenWidth.toLong())
        val visibleBottom = minOf(bottom, screenHeight.toLong())
        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) return null

        val totalArea = (right - left).toDouble() * (bottom - top).toDouble()
        val visibleArea = (visibleRight - visibleLeft).toDouble() * (visibleBottom - visibleTop).toDouble()
        if (visibleArea * 2 < totalArea) return null

        return TapPoint(
            ((visibleLeft + visibleRight) / 2).toInt(),
            ((visibleTop + visibleBottom) / 2).toInt()
        )
    }
}
