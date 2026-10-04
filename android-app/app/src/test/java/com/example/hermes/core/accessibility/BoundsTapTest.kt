package com.example.hermes.core.accessibility

import org.junit.Assert.*
import org.junit.Test

class BoundsTapTest {

    private val screenW = 1080
    private val screenH = 2400

    @Test
    fun centerOfAWellFormedRectangle() {
        // the format `observe` reports: "[left,top][right,bottom]"
        assertEquals(TapPoint(235, 885), BoundsTap.centerOfBounds("[120,850][350,920]", screenW, screenH))
    }

    @Test
    fun centerRoundsDownForOddSizes() {
        assertEquals(TapPoint(1, 1), BoundsTap.centerOfBounds("[0,0][3,3]", screenW, screenH))
    }

    @Test
    fun aPartlyOffScreenElementIsTappedAtTheCenterOfItsVisiblePart() {
        // 400 px wide, 300 of them visible: tap the middle of the visible 300, not of the whole box.
        assertEquals(TapPoint(150, 600), BoundsTap.centerOfBounds("[-100,500][300,700]", screenW, screenH))
        assertEquals(TapPoint(940, 600), BoundsTap.centerOfBounds("[800,500][1080,700]", screenW, screenH))
    }

    @Test
    fun anElementMostlyOffScreenIsRefusedBecauseItsTapTargetIsProbablyElsewhere() {
        assertNull(BoundsTap.centerOfBounds("[-400,500][-100,700]", screenW, screenH))     // entirely off
        assertNull(BoundsTap.centerOfBounds("[1000,500][1400,700]", screenW, screenH))     // 80 of 400 visible
        assertNull(BoundsTap.centerOfBounds("[0,2300][100,2700]", screenW, screenH))       // 100 of 400 visible
    }

    @Test
    fun exactlyHalfVisibleIsStillAccepted() {
        assertEquals(TapPoint(50, 600), BoundsTap.centerOfBounds("[-100,500][100,700]", screenW, screenH))
    }

    @Test
    fun emptyOrInvertedRectanglesAreRefused() {
        assertNull(BoundsTap.centerOfBounds("[100,100][100,300]", screenW, screenH))   // zero width
        assertNull(BoundsTap.centerOfBounds("[100,100][300,100]", screenW, screenH))   // zero height
        assertNull(BoundsTap.centerOfBounds("[300,300][100,100]", screenW, screenH))   // inverted
    }

    @Test
    fun malformedInputIsRefusedNotGuessed() {
        for (bad in listOf("", "[]", "[1,2]", "[a,b][c,d]", "1,2,3,4", "[1,2][3,4][5,6]", "[1,2][3]", "null")) {
            assertNull("input: '$bad'", BoundsTap.centerOfBounds(bad, screenW, screenH))
        }
    }

    @Test
    fun aNonPositiveScreenSizeRefusesEverything() {
        assertNull(BoundsTap.centerOfBounds("[0,0][10,10]", 0, screenH))
        assertNull(BoundsTap.centerOfBounds("[0,0][10,10]", screenW, -1))
    }

    @Test
    fun hugeValuesDoNotOverflow() {
        // (0 + Int.MAX) / 2 does not overflow and is far off screen
        assertNull(BoundsTap.centerOfBounds("[0,0][2147483647,2147483647]", screenW, screenH))
        assertNull(BoundsTap.centerOfBounds("[2147483646,2147483646][2147483647,2147483647]", screenW, screenH))
    }
}
