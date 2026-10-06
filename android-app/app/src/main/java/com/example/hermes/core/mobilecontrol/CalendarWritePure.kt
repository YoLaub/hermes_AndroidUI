package com.example.hermes.core.mobilecontrol

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class Problem(val code: String, val message: String)

/** What the phone knows about an existing event, read from the calendar provider just before a change. */
data class EventFacts(
    val title: String?,
    val startMs: Long,
    val endMs: Long,
    val location: String?,
    val calendarName: String?,
    val calendarWritable: Boolean,
    val calendarVisible: Boolean,
    val recurring: Boolean,
    val hasAttendees: Boolean,
    val allDay: Boolean
)

/** What the confirmation shows: a question and the exact change. */
data class ChangeText(val headline: String, val details: String)

/**
 * Pure rules for creating, modifying and deleting calendar events. Nothing here touches Android: the writer applies
 * these rules before it shows the user a confirmation, and applies the change only after the user's tap.
 */
object CalendarWritePure {
    const val MAX_TEXT_CHARS = 200
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS
    const val MAX_DURATION_MS = DAY_MS
    private const val PAST_LIMIT_MS = 365 * DAY_MS
    private const val FUTURE_LIMIT_MS = 730 * DAY_MS

    private val LOCAL_SHAPE = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?""")

    /** "2026-10-07T15:00" (seconds optional) read in [zone], or null if it is not exactly that shape and a real date. */
    fun parseLocal(text: String?, zone: TimeZone): Long? {
        val t = text?.trim() ?: return null
        if (!LOCAL_SHAPE.matches(t)) return null
        val pattern = if (t.length == 16) "yyyy-MM-dd'T'HH:mm" else "yyyy-MM-dd'T'HH:mm:ss"
        val fmt = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = zone; isLenient = false }
        val pos = ParsePosition(0)
        val date = fmt.parse(t, pos) ?: return null
        return if (pos.index == t.length) date.time else null
    }

    fun validateCreate(title: String?, startMs: Long?, endMs: Long?, location: String?, nowMs: Long): Problem? {
        if (title.isNullOrBlank()) return invalid("Le titre est requis.")
        if (title.length > MAX_TEXT_CHARS) return invalid("Le titre dépasse $MAX_TEXT_CHARS caractères.")
        if (location != null && location.length > MAX_TEXT_CHARS) return invalid("Le lieu dépasse $MAX_TEXT_CHARS caractères.")
        if (startMs == null || endMs == null) return invalid("Le début et la fin sont requis (format 2026-10-07T15:00).")
        return checkTimes(startMs, endMs, nowMs)
    }

    /** At least one field must change, and the event as it would become must be valid. */
    fun validateUpdate(
        newTitle: String?, newStartMs: Long?, newEndMs: Long?, newLocation: String?, current: EventFacts, nowMs: Long
    ): Problem? {
        if (newTitle == null && newStartMs == null && newEndMs == null && newLocation == null) {
            return invalid("Aucune modification demandée.")
        }
        if (newTitle != null && newTitle.isBlank()) return invalid("Le nouveau titre ne peut pas être vide.")
        if (newTitle != null && newTitle.length > MAX_TEXT_CHARS) return invalid("Le titre dépasse $MAX_TEXT_CHARS caractères.")
        if (newLocation != null && newLocation.length > MAX_TEXT_CHARS) return invalid("Le lieu dépasse $MAX_TEXT_CHARS caractères.")
        return checkTimes(newStartMs ?: current.startMs, newEndMs ?: current.endMs, nowMs)
    }

    /**
     * Which existing events may be modified or deleted: never one whose change would reach other people (attendees)
     * or a whole series (recurring), nor one in a calendar the user hides or cannot write.
     */
    fun editability(e: EventFacts): Problem? = when {
        e.recurring -> notEditable("L'événement est récurrent : un changement toucherait toute la série.")
        e.hasAttendees -> notEditable("L'événement a des invités : un changement leur enverrait une mise à jour.")
        !e.calendarWritable -> notEditable("Le calendrier de cet événement est en lecture seule.")
        !e.calendarVisible -> notEditable("Le calendrier de cet événement est masqué.")
        e.allDay -> notEditable("Les événements sur toute la journée ne sont pas pris en charge.")
        else -> null
    }

    fun createText(title: String, startMs: Long, endMs: Long, location: String?, calendarName: String?, zone: TimeZone) =
        ChangeText("Créer cet événement ?", describe(title, startMs, endMs, location, calendarName, zone))

    fun updateText(
        current: EventFacts, newTitle: String?, newStartMs: Long?, newEndMs: Long?, newLocation: String?, zone: TimeZone
    ): ChangeText {
        val lines = ArrayList<String>()
        if (newTitle != null && newTitle != current.title) lines += "Titre : « ${current.title ?: "(sans titre)"} » → « $newTitle »"
        if (newStartMs != null && newStartMs != current.startMs) {
            lines += "Début : ${stamp(current.startMs, zone)} → ${stamp(newStartMs, zone)}"
        }
        if (newEndMs != null && newEndMs != current.endMs) lines += "Fin : ${stamp(current.endMs, zone)} → ${stamp(newEndMs, zone)}"
        if (newLocation != null && newLocation != current.location) {
            lines += "Lieu : ${current.location?.takeIf { it.isNotBlank() } ?: "(aucun)"} → ${newLocation.ifBlank { "(aucun)" }}"
        }
        current.calendarName?.let { lines += "Calendrier : $it" }
        return ChangeText("Modifier cet événement ?", lines.joinToString("\n"))
    }

    fun deleteText(e: EventFacts, zone: TimeZone) =
        ChangeText("Supprimer cet événement ?", describe(e.title ?: "(sans titre)", e.startMs, e.endMs, e.location, e.calendarName, zone))

    private fun checkTimes(startMs: Long, endMs: Long, nowMs: Long): Problem? = when {
        endMs <= startMs -> invalid("La fin doit être après le début.")
        endMs - startMs > MAX_DURATION_MS -> invalid("Un événement dure 24 heures au plus.")
        startMs < nowMs - PAST_LIMIT_MS || startMs > nowMs + FUTURE_LIMIT_MS ->
            invalid("La date doit être dans l'année écoulée ou les deux années à venir.")
        else -> null
    }

    private fun describe(title: String, startMs: Long, endMs: Long, location: String?, calendarName: String?, zone: TimeZone): String {
        val sameDay = stamp(startMs, zone).substring(0, 10) == stamp(endMs, zone).substring(0, 10)
        val end = if (sameDay) stamp(endMs, zone).substring(11) else stamp(endMs, zone)
        val lines = arrayListOf("« $title »", "${stamp(startMs, zone)} → $end")
        if (!location.isNullOrBlank()) lines += "Lieu : $location"
        if (!calendarName.isNullOrBlank()) lines += "Calendrier : $calendarName"
        return lines.joinToString("\n")
    }

    private fun stamp(ms: Long, zone: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = zone }.format(java.util.Date(ms))

    private fun invalid(message: String) = Problem("INVALID_ARGUMENTS", message)
    private fun notEditable(message: String) = Problem("EVENT_NOT_EDITABLE", message)
}
