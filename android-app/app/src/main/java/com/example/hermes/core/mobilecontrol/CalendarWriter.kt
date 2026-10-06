package com.example.hermes.core.mobilecontrol

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalendarInfo(val id: Long, val name: String?)

/**
 * The device side of a confirmed calendar change. Nothing here runs without the user's tap on the confirmation, and
 * every rule about WHAT may be changed lives in [CalendarWritePure]. Not unit-testable on the JVM: needs a device check.
 */
class CalendarWriter(private val context: Context) {

    fun hasPermission(): Boolean = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** The calendar a new event goes to: visible, writable, the user's primary one first. */
    fun primaryWritableCalendar(): CalendarInfo? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
        )?.use { c -> if (c.moveToFirst()) return CalendarInfo(c.getLong(0), c.getString(1)) }
        return null
    }

    /** What the calendar holds for this event right now, or null if it does not exist (or was deleted). */
    fun readEvent(id: Long): EventFacts? {
        val resolver = context.contentResolver
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        var calendarId = -1L
        var title: String? = null
        var start = 0L
        var end = 0L
        var location: String? = null
        var recurring = false
        var allDay = false
        var attendees = false
        val found = resolver.query(
            uri,
            arrayOf(
                CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.RRULE,
                CalendarContract.Events.RDATE, CalendarContract.Events.ORIGINAL_ID, CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.HAS_ATTENDEE_DATA, CalendarContract.Events.DELETED
            ), null, null, null
        )?.use { c ->
            if (!c.moveToFirst() || c.getInt(10) == 1) return@use false
            title = c.getString(0)
            start = c.getLong(1)
            end = if (c.isNull(2)) start else c.getLong(2)
            location = c.getString(3)
            calendarId = c.getLong(4)
            recurring = !c.getString(5).isNullOrBlank() || !c.getString(6).isNullOrBlank() || !c.getString(7).isNullOrBlank()
            allDay = c.getInt(8) == 1
            attendees = c.getInt(9) == 1
            true
        } ?: false
        if (!found) return null

        var calendarName: String? = null
        var writable = false
        var visible = false
        resolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
            arrayOf(
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE
            ), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                calendarName = c.getString(0)
                writable = c.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                visible = c.getInt(2) == 1
            }
        }
        return EventFacts(title, start, end, location, calendarName, writable, visible, recurring, attendees, allDay)
    }

    /** Creates an event WITHOUT attendees. Returns its id, or null if the provider refused. */
    fun insert(calendarId: Long, title: String, startMs: Long, endMs: Long, location: String?, timeZoneId: String): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
            put(CalendarContract.Events.HAS_ATTENDEE_DATA, 0)
            if (!location.isNullOrBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        return ContentUris.parseId(uri)
    }

    /** Changes only the fields given. An empty location clears it. Returns the number of rows changed. */
    fun update(id: Long, title: String?, startMs: Long?, endMs: Long?, location: String?): Int {
        val values = ContentValues().apply {
            if (title != null) put(CalendarContract.Events.TITLE, title)
            if (startMs != null) put(CalendarContract.Events.DTSTART, startMs)
            if (endMs != null) put(CalendarContract.Events.DTEND, endMs)
            if (location != null) {
                if (location.isBlank()) putNull(CalendarContract.Events.EVENT_LOCATION)
                else put(CalendarContract.Events.EVENT_LOCATION, location)
            }
        }
        return context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values, null, null)
    }

    fun delete(id: Long): Int =
        context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
}
