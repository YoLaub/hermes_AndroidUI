package com.example.hermes.features.workspace

import com.example.hermes.core.model.AgentNote
import com.example.hermes.core.model.KanbanColumn
import com.example.hermes.core.model.KanbanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.example.hermes.features.workspace.calendar.AgentRun
import com.example.hermes.features.workspace.calendar.PhoneEvent
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

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
    fun `agenda summary is today's events and runs, earliest first`() {
        val today = LocalDate.of(2026, 10, 19)
        fun at(time: String) = LocalDateTime.parse("2026-10-19T$time")
        val items = listOf(
            PhoneEvent(1, "Tard", at("16:00"), at("17:00"), null, false),
            AgentRun("j", "Point formation", at("10:00"), "memtest", null),
            PhoneEvent(2, "Demain", at("08:00").plusDays(1), at("09:00").plusDays(1), null, false),
        )
        assertEquals(AgendaSummary(todayCount = 2, next = "10:00 Point formation"), agendaSummary(items, today))
        assertEquals(AgendaSummary(0, null), agendaSummary(emptyList(), today))
    }
}
