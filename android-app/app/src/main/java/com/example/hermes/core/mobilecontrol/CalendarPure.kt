package com.example.hermes.core.mobilecontrol

/** A calendar row as read from Android, before filtering. Never leaves this layer as is. */
data class RawCalendarRow(
    val title: String?,
    val beginMs: Long,
    val endMs: Long,
    val location: String?,
    val allDay: Boolean,
    /** The calendar provider's id of the event, so the agent can name the one to change. */
    val eventId: Long? = null
)

/**
 * Pure rules for the calendar read: how far ahead, which fields, how many events.
 * Attendees, notes and organiser are never read, so they cannot leak.
 */
object CalendarPure {
    const val DEFAULT_DAYS = 7
    const val MAX_DAYS = 7
    const val MAX_EVENTS = 50
    const val MAX_TEXT_CHARS = 200
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun clampDays(days: Int?): Int = (days ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)

    /** From now to at most [MAX_DAYS] days ahead. */
    fun window(nowMs: Long, days: Int?): Pair<Long, Long> = nowMs to nowMs + clampDays(days) * DAY_MS

    fun toEvents(rows: List<RawCalendarRow>): List<MobileCalendarEvent> =
        rows.sortedBy { it.beginMs }
            .take(MAX_EVENTS)
            .map {
                MobileCalendarEvent(
                    title = clean(it.title),
                    startMs = it.beginMs,
                    endMs = maxOf(it.endMs, it.beginMs),
                    location = clean(it.location),
                    allDay = it.allDay,
                    eventId = it.eventId?.toString()
                )
            }

    private fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT_CHARS)
}
