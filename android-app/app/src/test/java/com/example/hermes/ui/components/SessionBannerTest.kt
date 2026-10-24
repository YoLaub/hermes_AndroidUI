package com.example.hermes.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionBannerTest {
    @Test
    fun `elapsed time reads as HH mm ss`() {
        assertEquals("00:00:00", formatElapsed(0))
        assertEquals("00:04:12", formatElapsed(252_000))
        assertEquals("01:00:05", formatElapsed(3_605_999))
    }

    @Test
    fun `a clock running behind the start shows zero`() {
        assertEquals("00:00:00", formatElapsed(-5_000))
    }

    @Test
    fun `sessions past a day keep counting hours`() {
        assertEquals("25:00:00", formatElapsed(90_000_000))
    }
}
