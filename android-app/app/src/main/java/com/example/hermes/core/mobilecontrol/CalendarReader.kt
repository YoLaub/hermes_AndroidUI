package com.example.hermes.core.mobilecontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * Reads upcoming events through the Android calendar provider, only the columns the user consented to share.
 * Attendees, notes and organiser are never requested. Not unit-testable on the JVM: needs a device check.
 */
class CalendarReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events of calendars the user shows, in start order, at most [CalendarPure.MAX_EVENTS] rows. */
    fun read(nowMs: Long, days: Int?): List<RawCalendarRow> {
        val (from, to) = CalendarPure.window(nowMs, days)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.toString())
            .appendPath(to.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID
        )
        val rows = ArrayList<RawCalendarRow>()
        context.contentResolver.query(
            uri, projection,
            "${CalendarContract.Instances.VISIBLE} = 1", null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext() && rows.size < CalendarPure.MAX_EVENTS) {
                rows += RawCalendarRow(
                    title = c.getString(0),
                    beginMs = c.getLong(1),
                    endMs = c.getLong(2),
                    location = c.getString(3),
                    allDay = c.getInt(4) == 1,
                    eventId = c.getLong(5)
                )
            }
        }
        return rows
    }
}
