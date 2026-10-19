package com.example.hermes.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountBadgeTest {
    @Test
    fun `nothing is shown for zero or a negative count`() {
        assertNull(countBadgeText(0))
        assertNull(countBadgeText(-3))
    }

    @Test
    fun `counts up to 99 are shown as is`() {
        assertEquals("1", countBadgeText(1))
        assertEquals("42", countBadgeText(42))
        assertEquals("99", countBadgeText(99))
    }

    @Test
    fun `counts above 99 are capped`() {
        assertEquals("99+", countBadgeText(100))
        assertEquals("99+", countBadgeText(5000))
    }
}
