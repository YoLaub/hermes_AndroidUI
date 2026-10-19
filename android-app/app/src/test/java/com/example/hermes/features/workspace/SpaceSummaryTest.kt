package com.example.hermes.features.workspace

import com.example.hermes.core.model.AgentNote
import com.example.hermes.core.model.CalendarEvent
import com.example.hermes.core.model.KanbanColumn
import com.example.hermes.core.model.KanbanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SpaceSummaryTest {
    private val columns = listOf(
        KanbanColumn("ready", listOf(KanbanTask("a"), KanbanTask("b"))),
        KanbanColumn("running", listOf(KanbanTask("c"))),
        KanbanColumn("blocked", listOf(KanbanTask("d"))),
        KanbanColumn("done", listOf(KanbanTask("e"))),
    )

    @Test
    fun `tasks summary counts blocked and running`() {
        assertEquals(TasksSummary(blocked = 1, running = 1), tasksSummary(columns))
        assertEquals(TasksSummary(0, 0), tasksSummary(emptyList()))
    }

    @Test
    fun `Lancer starts the ready tasks`() {
        assertEquals(2, readyCount(columns))
        assertEquals(0, readyCount(emptyList()))
    }

    @Test
    fun `notes summary gives the count and the pinned one`() {
        val notes = listOf(AgentNote(title = "a", content = ""), AgentNote(title = "Objectifs", content = "", isPinned = true))
        assertEquals(NotesSummary(count = 2, pinnedTitle = "Objectifs"), notesSummary(notes))
        assertNull(notesSummary(listOf(AgentNote(title = "a", content = ""))).pinnedTitle)
    }

    @Test
    fun `agenda summary is today's slots, earliest first`() {
        val today = LocalDate.of(2026, 10, 19)
        val events = listOf(
            CalendarEvent(title = "Tard", dateEpochDay = today.toEpochDay(), startTime = "16:00", endTime = "17:00"),
            CalendarEvent(title = "Point formation", dateEpochDay = today.toEpochDay(), startTime = "10:00", endTime = "11:00"),
            CalendarEvent(title = "Demain", dateEpochDay = today.plusDays(1).toEpochDay(), startTime = "08:00", endTime = "09:00"),
        )
        assertEquals(AgendaSummary(todayCount = 2, next = "10:00 Point formation"), agendaSummary(events, today))
        assertEquals(AgendaSummary(0, null), agendaSummary(emptyList(), today))
    }
}
