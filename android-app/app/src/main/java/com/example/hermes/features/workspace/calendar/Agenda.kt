package com.example.hermes.features.workspace.calendar

import com.example.hermes.core.mobilecontrol.RawCalendarRow
import com.example.hermes.core.model.CronJob
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The profile that runs a job saved without one. */
const val DEFAULT_PROFILE = "default"

/** One line of the agenda: an event of the phone's calendar or an agent's next scheduled run. */
sealed interface AgendaItem {
    val key: String
    val title: String
    val start: LocalDateTime

    /** The days the item covers, first to last. */
    fun days(): List<LocalDate>
}

data class PhoneEvent(
    val eventId: Long,
    override val title: String,
    override val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String?,
    val allDay: Boolean,
) : AgendaItem {
    override val key get() = "event-$eventId-$start"

    override fun days(): List<LocalDate> {
        // An end at midnight (always so for all-day events) belongs to the day before.
        val last = if (end > start && end.toLocalTime() == java.time.LocalTime.MIDNIGHT) end.toLocalDate().minusDays(1) else end.toLocalDate()
        return generateSequence(start.toLocalDate()) { it.plusDays(1) }.takeWhile { it <= maxOf(last, start.toLocalDate()) }.toList()
    }
}

data class AgentRun(
    val jobId: String,
    override val title: String,
    override val start: LocalDateTime,
    val profile: String,
    val schedule: String?,
) : AgendaItem {
    override val key get() = "run-$jobId"
    override fun days() = listOf(start.toLocalDate())
}

sealed interface AgendaFilter {
    data object All : AgendaFilter
    data object Phone : AgendaFilter
    data class Profile(val name: String) : AgendaFilter
}

private const val TITLE_CHARS = 80

/** Phone rows in the user's time zone; all-day events are stored at UTC midnight, so they are read in UTC. */
fun phoneEvents(rows: List<RawCalendarRow>, zone: ZoneId): List<PhoneEvent> = rows.map { row ->
    val readZone = if (row.allDay) ZoneOffset.UTC else zone
    fun local(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), readZone)
    PhoneEvent(
        eventId = row.eventId ?: -1,
        title = row.title?.trim().orEmpty(),
        start = local(row.beginMs),
        end = local(maxOf(row.endMs, row.beginMs)),
        location = row.location?.trim()?.takeIf { it.isNotEmpty() },
        allDay = row.allDay,
    )
}

/** Jobs that will run again, at their next run; a job without a name is titled by its prompt. */
fun agentRuns(jobs: List<CronJob>, zone: ZoneId): List<AgentRun> = jobs.mapNotNull { job ->
    val next = job.nextRunAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
    AgentRun(
        jobId = job.id,
        title = (job.name?.takeIf { it.isNotBlank() } ?: job.prompt.lineSequence().firstOrNull().orEmpty()).take(TITLE_CHARS),
        start = next.atZoneSameInstant(zone).toLocalDateTime(),
        profile = job.profile ?: DEFAULT_PROFILE,
        schedule = job.scheduleDisplay,
    )
}

fun List<AgendaItem>.filteredBy(filter: AgendaFilter): List<AgendaItem> = filter { item ->
    when (filter) {
        AgendaFilter.All -> true
        AgendaFilter.Phone -> item is PhoneEvent
        is AgendaFilter.Profile -> item is AgentRun && item.profile.equals(filter.name, ignoreCase = true)
    }
}.sortedBy { it.start }

fun List<AgendaItem>.byDay(): Map<LocalDate, List<AgendaItem>> =
    flatMap { item -> item.days().map { it to item } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, items) -> items.sortedBy { it.start } }
