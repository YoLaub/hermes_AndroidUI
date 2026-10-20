package com.example.hermes.features.workspace

import com.example.hermes.core.model.AgentNote
import com.example.hermes.core.model.KanbanColumn
import com.example.hermes.features.workspace.calendar.AgendaItem
import com.example.hermes.features.workspace.calendar.byDay
import java.time.LocalDate

data class TasksSummary(val blocked: Int, val running: Int)

private fun List<KanbanColumn>.sizeOf(name: String) = firstOrNull { it.name.equals(name, ignoreCase = true) }?.tasks?.size ?: 0

fun tasksSummary(columns: List<KanbanColumn>) = TasksSummary(blocked = columns.sizeOf("blocked"), running = columns.sizeOf("running"))

/** What « Lancer » hands to the agents: the tasks marked ready. */
fun readyCount(columns: List<KanbanColumn>): Int = columns.sizeOf("ready")

data class NotesSummary(val count: Int, val pinnedTitle: String?)

fun notesSummary(notes: List<AgentNote>) = NotesSummary(notes.size, notes.firstOrNull { it.isPinned }?.title)

data class AgendaSummary(val todayCount: Int, val next: String?)

fun agendaSummary(items: List<AgendaItem>, today: LocalDate = LocalDate.now()): AgendaSummary {
    val todays = items.byDay()[today].orEmpty()
    return AgendaSummary(todays.size, todays.firstOrNull()?.let { "%02d:%02d %s".format(it.start.hour, it.start.minute, it.title) })
}

enum class EventFormError(val message: String) {
    Title("Donnez un titre au créneau"),
    TimeFormat("Heures au format HH:mm, ex. 09:30"),
    EndBeforeStart("La fin doit suivre le début"),
}

private val HH_MM = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")

private fun minutesOf(time: String): Int? = HH_MM.find(time.trim())?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }

/** Why a slot cannot be saved: no title, an unreadable time, or an end not after the start. */
fun eventFormError(title: String, start: String, end: String): EventFormError? {
    if (title.isBlank()) return EventFormError.Title
    val s = minutesOf(start) ?: return EventFormError.TimeFormat
    val e = minutesOf(end) ?: return EventFormError.TimeFormat
    return if (e <= s) EventFormError.EndBeforeStart else null
}
