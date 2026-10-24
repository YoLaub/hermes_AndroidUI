package com.example.hermes.features.workspace.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.hermes.core.mobilecontrol.CalendarWriter
import com.example.hermes.core.mobilecontrol.RawCalendarRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The owner's own calendar through the Android provider, for the agenda screen. The agent's access to the
 * calendar does not go through here: it is the mobile-control consent. Not unit-testable on the JVM.
 */
class DevicePhoneCalendar(private val context: Context, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : PhoneCalendar {
    private val writer = CalendarWriter(context)

    override fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun events(from: LocalDate, until: LocalDate): List<PhoneEvent> = withContext(Dispatchers.IO) {
        val zone = zone()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.atStartOfDay(zone).toInstant().toEpochMilli().toString())
            .appendPath(until.atStartOfDay(zone).toInstant().toEpochMilli().toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.RRULE, CalendarContract.Instances.ORIGINAL_ID, CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
        )
        val rows = ArrayList<RawCalendarRow>()
        val fixed = HashSet<Long>()
        context.contentResolver.query(
            uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(5)
                rows += RawCalendarRow(c.getString(0), c.getLong(1), c.getLong(2), c.getString(3), c.getInt(4) == 1, id)
                val repeating = !c.getString(6).isNullOrBlank() || !c.isNull(7)
                if (repeating || c.getInt(8) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) fixed += id
            }
        }
        phoneEvents(rows, zone).map { if (it.eventId in fixed) it.copy(editable = false) else it }
    }

    override suspend fun create(draft: EventDraft): Boolean = withContext(Dispatchers.IO) {
        if (!writer.hasPermission()) return@withContext false
        val calendar = writer.primaryWritableCalendar() ?: return@withContext false
        val (start, end) = millis(draft)
        writer.insert(calendar.id, draft.title, start, end, draft.location.ifBlank { null }, zone().id) != null
    }

    override suspend fun update(eventId: Long, draft: EventDraft): Boolean = withContext(Dispatchers.IO) {
        if (!writer.hasPermission()) return@withContext false
        val (start, end) = millis(draft)
        writer.update(eventId, draft.title, start, end, draft.location) > 0
    }

    override suspend fun delete(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        writer.hasPermission() && writer.delete(eventId) > 0
    }

    private fun millis(draft: EventDraft): Pair<Long, Long> {
        fun at(time: String) = draft.date.atTime(LocalTime.parse(time.padStart(5, '0'))).atZone(zone()).toInstant().toEpochMilli()
        return at(draft.start) to at(draft.end)
    }
}
