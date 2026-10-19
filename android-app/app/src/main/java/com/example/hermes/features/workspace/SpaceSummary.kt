package com.example.hermes.features.workspace

import com.example.hermes.core.model.AgentNote
import com.example.hermes.core.model.CalendarEvent
import com.example.hermes.core.model.KanbanColumn
import java.time.LocalDate

data class TasksSummary(val blocked: Int, val running: Int)

private fun List<KanbanColumn>.sizeOf(name: String) = firstOrNull { it.name.equals(name, ignoreCase = true) }?.tasks?.size ?: 0

fun tasksSummary(columns: List<KanbanColumn>) = TasksSummary(blocked = columns.sizeOf("blocked"), running = columns.sizeOf("running"))

/** What « Lancer » hands to the agents: the tasks marked ready. */
fun readyCount(columns: List<KanbanColumn>): Int = columns.sizeOf("ready")

data class NotesSummary(val count: Int, val pinnedTitle: String?)

fun notesSummary(notes: List<AgentNote>) = NotesSummary(notes.size, notes.firstOrNull { it.isPinned }?.title)

data class AgendaSummary(val todayCount: Int, val next: String?)

fun agendaSummary(events: List<CalendarEvent>, today: LocalDate = LocalDate.now()): AgendaSummary {
    val todays = events.filter { it.dateEpochDay == today.toEpochDay() }.sortedBy { it.startTime }
    return AgendaSummary(todays.size, todays.firstOrNull()?.let { "${it.startTime} ${it.title}" })
}
