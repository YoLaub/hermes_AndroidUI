package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class CalendarPureTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun daysDefaultToSevenAndAreClampedToOneThroughSeven() {
        assertEquals(7, CalendarPure.clampDays(null))
        assertEquals(1, CalendarPure.clampDays(0))
        assertEquals(1, CalendarPure.clampDays(-5))
        assertEquals(3, CalendarPure.clampDays(3))
        assertEquals(7, CalendarPure.clampDays(30))
    }

    @Test
    fun theWindowStartsNowAndNeverExceedsSevenDays() {
        val (from, to) = CalendarPure.window(now, 365)
        assertEquals(now, from)
        assertEquals(now + 7 * day, to)
    }

    private fun raw(title: String? = "Standup", begin: Long = now + 1000, end: Long = now + 2000,
                    location: String? = "Room 1", allDay: Boolean = false) =
        RawCalendarRow(title, begin, end, location, allDay)

    @Test
    fun onlyTheFiveAllowedFieldsSurvive() {
        val e = CalendarPure.toEvents(listOf(raw())).single()
        assertEquals("Standup", e.title)
        assertEquals(now + 1000, e.startMs)
        assertEquals(now + 2000, e.endMs)
        assertEquals("Room 1", e.location)
        assertFalse(e.allDay)
    }

    @Test
    fun blankTitleAndLocationBecomeNullAndLongTextIsCut() {
        val e = CalendarPure.toEvents(listOf(raw(title = "  ", location = "x".repeat(500)))).single()
        assertNull(e.title)
        assertEquals(CalendarPure.MAX_TEXT_CHARS, e.location!!.length)
        assertNull(CalendarPure.toEvents(listOf(raw(location = ""))).single().location)
    }

    @Test
    fun eventsAreSortedByStartAndCappedAtFifty() {
        val rows = (100 downTo 1).map { raw(title = "e$it", begin = now + it * 1000L, end = now + it * 1000L + 500) }
        val events = CalendarPure.toEvents(rows)
        assertEquals(CalendarPure.MAX_EVENTS, events.size)
        assertEquals("e1", events.first().title)
        assertTrue(events.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
    }

    @Test
    fun anEventEndingBeforeItStartsIsKeptWithEndEqualToStart() {
        val e = CalendarPure.toEvents(listOf(raw(begin = now + 5000, end = now + 1000))).single()
        assertEquals(e.startMs, e.endMs)
    }

    @Test
    fun theEventIdIsKeptSoTheAgentCanNameTheEventToChange() {
        val e = CalendarPure.toEvents(listOf(RawCalendarRow("Standup", now + 1000, now + 2000, null, false, eventId = 4242L))).single()
        assertEquals("4242", e.eventId)
        assertNull(CalendarPure.toEvents(listOf(RawCalendarRow("x", now + 1, now + 2, null, false))).single().eventId)
    }
}
