package com.example.hermes.core.accessibility

import org.junit.Assert.*
import org.junit.Test

class ScreenshotPureTest {

    // ── size: downscale to a long side, never upscale, keep the aspect ratio ─────

    @Test
    fun aLargeScreenIsScaledDownToTheMaximumLongSide() {
        assertEquals(ImageSize(576, 1280), ScreenshotGeometry.targetSize(1080, 2400, 1280))
        assertEquals(ImageSize(1280, 576), ScreenshotGeometry.targetSize(2400, 1080, 1280))
    }

    @Test
    fun aSmallScreenIsNeverUpscaled() {
        assertEquals(ImageSize(720, 1280), ScreenshotGeometry.targetSize(720, 1280, 1280))
        assertEquals(ImageSize(400, 800), ScreenshotGeometry.targetSize(400, 800, 1280))
    }

    @Test
    fun degenerateSizesGiveAtLeastOnePixelOrNull() {
        assertNull(ScreenshotGeometry.targetSize(0, 100, 1280))
        assertNull(ScreenshotGeometry.targetSize(100, -5, 1280))
        assertNull(ScreenshotGeometry.targetSize(100, 100, 0))
        val tiny = ScreenshotGeometry.targetSize(5000, 1, 1280)!!
        assertTrue(tiny.width == 1280 && tiny.height >= 1)
    }

    // ── tap: screenshot pixels back to screen pixels ──────────────────────────────

    @Test
    fun aPointOnTheScaledImageMapsBackToTheScreen() {
        // screen 1080x2400, image 576x1280 (scale 1.875)
        assertEquals(TapPoint(540, 1200), ScreenshotGeometry.toScreenPoint(288, 640, ImageSize(576, 1280), 1080, 2400))
        assertEquals(TapPoint(0, 0), ScreenshotGeometry.toScreenPoint(0, 0, ImageSize(576, 1280), 1080, 2400))
    }

    @Test
    fun aWindowThatDoesNotStartAtTheScreenOriginIsMappedWithItsOffset() {
        // split-screen: the window is 1080 wide, 1200 tall and starts at y = 1200
        assertEquals(
            TapPoint(540, 1200 + 600),
            ScreenshotGeometry.toScreenPoint(288, 320, ImageSize(576, 640), 1080, 1200, offsetX = 0, offsetY = 1200)
        )
        assertEquals(
            TapPoint(100 + 540, 50),
            ScreenshotGeometry.toScreenPoint(288, 0, ImageSize(576, 640), 1080, 1200, offsetX = 100, offsetY = 50)
        )
    }

    @Test
    fun theLastPixelStaysInsideTheScreen() {
        val p = ScreenshotGeometry.toScreenPoint(575, 1279, ImageSize(576, 1280), 1080, 2400)!!
        assertTrue(p.x in 0 until 1080 && p.y in 0 until 2400)
    }

    @Test
    fun pointsOutsideTheImageAreRefused() {
        val img = ImageSize(576, 1280)
        assertNull(ScreenshotGeometry.toScreenPoint(-1, 10, img, 1080, 2400))
        assertNull(ScreenshotGeometry.toScreenPoint(10, -1, img, 1080, 2400))
        assertNull(ScreenshotGeometry.toScreenPoint(576, 10, img, 1080, 2400))
        assertNull(ScreenshotGeometry.toScreenPoint(10, 1280, img, 1080, 2400))
    }

    // ── redaction: black out password fields, in image coordinates ────────────────

    @Test
    fun passwordFieldsAreMappedToImageCoordinatesWithAMargin() {
        // field at screen [100,400][700,520]; scale 576/1080 = 0.5333...
        val rects = Redaction.imageRects(listOf(ScreenRect(100, 400, 700, 520)), ScreenRect(0, 0, 1080, 2400), ImageSize(576, 1280), margin = 4)
        assertEquals(1, rects.size)
        val r = rects[0]
        assertTrue(r.left <= 53 && r.top <= 213 && r.right >= 373 && r.bottom >= 277)   // covers the field
        assertTrue(r.left >= 0 && r.top >= 0 && r.right <= 576 && r.bottom <= 1280)     // stays in the image
    }

    @Test
    fun rectsAreClampedAndEmptyOrOffScreenOnesDropped() {
        val rects = Redaction.imageRects(
            listOf(ScreenRect(-50, -50, 100, 100), ScreenRect(5000, 5000, 6000, 6000), ScreenRect(10, 10, 10, 50)),
            ScreenRect(0, 0, 1080, 2400), ImageSize(576, 1280), margin = 0
        )
        assertEquals(1, rects.size)
        assertEquals(0, rects[0].left); assertEquals(0, rects[0].top)
    }

    @Test
    fun noPasswordFieldMeansNothingToMask() {
        assertTrue(Redaction.imageRects(emptyList(), ScreenRect(0, 0, 1080, 2400), ImageSize(576, 1280), 4).isEmpty())
    }

    @Test
    fun aPasswordFieldIsFoundInWindowCoordinatesWhenTheWindowIsOffset() {
        // window = bottom half of a 2400 px screen; the field is at screen y 1500..1560
        val rects = Redaction.imageRects(
            listOf(ScreenRect(100, 1500, 700, 1560)), ScreenRect(0, 1200, 1080, 2400), ImageSize(540, 600), margin = 0
        )
        assertEquals(1, rects.size)
        assertEquals(150, rects[0].top)      // (1500 - 1200) * 0.5
        assertEquals(180, rects[0].bottom)   // (1560 - 1200) * 0.5
    }

    @Test
    fun aFieldOutsideTheCapturedWindowIsIgnored() {
        val rects = Redaction.imageRects(
            listOf(ScreenRect(100, 100, 700, 160)), ScreenRect(0, 1200, 1080, 2400), ImageSize(540, 600), margin = 0
        )
        assertTrue(rects.isEmpty())
    }

    // ── JPEG budget: keep the first encoding that fits, and only that one ─────────

    private fun fakeEncode(sizes: Map<Int, Int>) = { q: Int -> ByteArray(sizes.getValue(q)) { q.toByte() } }

    @Test
    fun theFirstQualityThatFitsIsKept() {
        val r = JpegBudget.firstThatFits(1_000_000, JpegBudget.QUALITIES, fakeEncode(mapOf(70 to 1_500_000, 50 to 900_000, 35 to 500_000)))!!
        assertEquals(50, r.quality)
        assertEquals(900_000, r.bytes.size)
    }

    @Test
    fun theBestQualityIsKeptWhenItAlreadyFits() {
        assertEquals(70, JpegBudget.firstThatFits(1_000_000, JpegBudget.QUALITIES) { ByteArray(200_000) }!!.quality)
    }

    @Test
    fun ifNothingFitsThereIsNoResult() {
        assertNull(JpegBudget.firstThatFits(1_000_000, JpegBudget.QUALITIES) { ByteArray(2_000_000) })
    }

    @Test
    fun theLadderStopsAsSoonAsOneFits() {
        var calls = 0
        JpegBudget.firstThatFits(1_000_000, JpegBudget.QUALITIES) { calls++; ByteArray(100) }
        assertEquals(1, calls)
    }

    // ── Android error codes → our codes ───────────────────────────────────────────

    @Test
    fun androidScreenshotErrorsMapToDistinctCodes() {
        assertEquals("ACCESSIBILITY_DISABLED", ScreenshotErrors.toCode(2))          // NO_ACCESSIBILITY_ACCESS
        assertEquals("SCREENSHOT_TOO_FAST", ScreenshotErrors.toCode(3))             // INTERVAL_TIME_SHORT
        assertEquals("SCREENSHOT_BLOCKED_SECURE_WINDOW", ScreenshotErrors.toCode(6)) // SECURE_WINDOW
        for (other in listOf(1, 4, 5, 99, -1)) assertEquals("ACTION_FAILED", ScreenshotErrors.toCode(other))
    }

    @Test
    fun screenshotsNeedAndroid14BecauseOnlyThenCanASingleWindowBeCaptured() {
        // Before API 34 the only option is the whole display, which would include other apps' notifications.
        assertFalse(ScreenshotErrors.isSupported(30))
        assertFalse(ScreenshotErrors.isSupported(33))
        assertTrue(ScreenshotErrors.isSupported(34))
        assertTrue(ScreenshotErrors.isSupported(36))
    }

    // ── a tap by coordinates only makes sense for the screenshot it was read on ───

    private val window = ScreenRect(0, 0, 1080, 2400)
    private fun ctx(at: Long = 1_000L, w: ScreenRect = window) = ScreenshotContext("rev_9", ImageSize(576, 1280), w, at)

    @Test
    fun aTapNeedsTheRevisionOfTheLastScreenshot() {
        val c = ctx()
        assertEquals(TapPoint(540, 1200), c.toScreenPoint("rev_9", 288, 640, nowMs = 2_000L))
        assertNull(c.toScreenPoint("rev_8", 288, 640, nowMs = 2_000L))     // another screen state
        assertNull(c.toScreenPoint(null, 288, 640, nowMs = 2_000L))
        assertNull(c.toScreenPoint("rev_9", 9999, 640, nowMs = 2_000L))    // outside the image
    }

    @Test
    fun aScreenshotGoesStaleEvenIfNothingObservedTheScreenSince() {
        val c = ctx(at = 1_000L)
        assertNotNull(c.toScreenPoint("rev_9", 288, 640, nowMs = 1_000L + ScreenshotContext.MAX_AGE_MS))
        assertNull(c.toScreenPoint("rev_9", 288, 640, nowMs = 1_000L + ScreenshotContext.MAX_AGE_MS + 1))
    }

    @Test
    fun aClockGoingBackwardsIsNotTrusted() {
        assertNull(ctx(at = 5_000L).toScreenPoint("rev_9", 288, 640, nowMs = 4_000L))
    }

    @Test
    fun theWindowOriginIsAddedWhenMappingBackToTheScreen() {
        val c = ctx(w = ScreenRect(0, 1200, 1080, 2400)).copy(image = ImageSize(576, 640))
        assertEquals(TapPoint(540, 1200 + 600), c.toScreenPoint("rev_9", 288, 320, nowMs = 2_000L))
    }

    @Test
    fun anEmptyWindowRefusesEverything() {
        assertNull(ctx(w = ScreenRect(0, 0, 0, 0)).toScreenPoint("rev_9", 1, 1, nowMs = 2_000L))
    }
}
