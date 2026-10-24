package com.example.hermes.features.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventFormTest {
    @Test
    fun `a valid slot has a title and an end after its start`() {
        assertNull(eventFormError("Point formation", "09:30", "10:00"))
        assertEquals(EventFormError.Title, eventFormError(" ", "09:30", "10:00"))
    }

    @Test
    fun `times are HH mm`() {
        assertEquals(EventFormError.TimeFormat, eventFormError("x", "9h30", "10:00"))
        assertEquals(EventFormError.TimeFormat, eventFormError("x", "09:30", "25:00"))
        assertNull(eventFormError("x", "9:05", "23:59"))
    }

    @Test
    fun `the end must come after the start`() {
        assertEquals(EventFormError.EndBeforeStart, eventFormError("x", "10:00", "10:00"))
        assertEquals(EventFormError.EndBeforeStart, eventFormError("x", "11:00", "10:00"))
    }
}
