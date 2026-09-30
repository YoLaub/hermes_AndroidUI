package com.example.hermes.core.data

import android.content.Context
import com.example.hermes.core.model.CalendarEvent
import com.example.hermes.core.model.CalendarPermissionLevel
import com.example.hermes.core.model.EventStatus
import com.example.hermes.core.model.ProfileCalendarPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

class CalendarRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val eventsFile = File(context.filesDir, "agent_calendar.json")
    private val permissionsFile = File(context.filesDir, "calendar_permissions.json")

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _permissions = MutableStateFlow<List<ProfileCalendarPermission>>(emptyList())
    val permissions: StateFlow<List<ProfileCalendarPermission>> = _permissions.asStateFlow()

    suspend fun loadData(): Pair<List<CalendarEvent>, List<ProfileCalendarPermission>> = withContext(Dispatchers.IO) {
        // 1. Load Permissions
        val loadedPerms = try {
            if (permissionsFile.exists()) {
                val text = permissionsFile.readText()
                if (text.isNotBlank()) json.decodeFromString<List<ProfileCalendarPermission>>(text) else null
            } else null
        } catch (_: Exception) { null } ?: defaultPermissions()

        _permissions.value = loadedPerms
        savePermissionsToFile(loadedPerms)

        // 2. Load Events
        val todayEpochDay = LocalDate.now().toEpochDay()
        val loadedEvents = try {
            if (eventsFile.exists()) {
                val text = eventsFile.readText()
                if (text.isNotBlank()) json.decodeFromString<List<CalendarEvent>>(text) else null
            } else null
        } catch (_: Exception) { null } ?: defaultEvents(todayEpochDay)

        _events.value = loadedEvents.sortedWith(compareBy<CalendarEvent> { it.dateEpochDay }.thenBy { it.startTime })
        saveEventsToFile(_events.value)

        return@withContext Pair(_events.value, _permissions.value)
    }

    suspend fun saveEvent(event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        val current = _events.value.toMutableList()
        val index = current.indexOfFirst { it.id == event.id }
        val updated = event.copy(updatedAt = System.currentTimeMillis())

        if (index != -1) {
            current[index] = updated
        } else {
            current.add(updated)
        }

        val sorted = current.sortedWith(compareBy<CalendarEvent> { it.dateEpochDay }.thenBy { it.startTime })
        _events.value = sorted
        saveEventsToFile(sorted)
        return@withContext updated
    }

    suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        val current = _events.value.toMutableList()
        current.removeAll { it.id == eventId }
        _events.value = current
        saveEventsToFile(current)
    }

    suspend fun updatePermissions(perms: List<ProfileCalendarPermission>) = withContext(Dispatchers.IO) {
        _permissions.value = perms
        savePermissionsToFile(perms)
    }

    private fun defaultPermissions(): List<ProfileCalendarPermission> = listOf(
        ProfileCalendarPermission("Yoann", "user", CalendarPermissionLevel.ADMIN, "#58A6FF"),
        ProfileCalendarPermission("Mario", "agent", CalendarPermissionLevel.READ_WRITE, "#7EE787"),
        ProfileCalendarPermission("Gaston", "agent", CalendarPermissionLevel.READ_WRITE, "#D2A8FF"),
        ProfileCalendarPermission("Hermes", "agent", CalendarPermissionLevel.READ_ONLY, "#F0883E")
    )

    private fun defaultEvents(todayEpochDay: Long): List<CalendarEvent> = listOf(
        CalendarEvent(
            title = "📞 Point de calage Mario - Prospects Morbihan",
            description = "Validation des 6 PME identifiées et revue du workflow Kanban commercial.",
            dateEpochDay = todayEpochDay,
            startTime = "10:00",
            endTime = "10:45",
            organizer = "Mario",
            assignedProfiles = listOf("Yoann", "Mario"),
            status = EventStatus.CONFIRMED,
            locationOrLink = "Telegram / Hermes Call"
        ),
        CalendarEvent(
            title = "🔄 Sync auto Gaston - Export CRM",
            description = "Extraction hebdomadaire et synchronisation des données prospects.",
            dateEpochDay = todayEpochDay,
            startTime = "14:30",
            endTime = "15:00",
            organizer = "Gaston",
            assignedProfiles = listOf("Gaston"),
            status = EventStatus.CONFIRMED,
            locationOrLink = "Serveur Backend"
        ),
        CalendarEvent(
            title = "🏛️ Réunion stratégique de l'équipe Agents",
            description = "Coordination générale : objectifs hebdo, automatisation et débrief.",
            dateEpochDay = todayEpochDay + 1,
            startTime = "11:00",
            endTime = "12:00",
            organizer = "Yoann",
            assignedProfiles = listOf("Yoann", "Mario", "Gaston"),
            status = EventStatus.CONFIRMED,
            locationOrLink = "Forum Agents Hub"
        )
    )

    private fun saveEventsToFile(events: List<CalendarEvent>) {
        try {
            eventsFile.writeText(json.encodeToString(events))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePermissionsToFile(perms: List<ProfileCalendarPermission>) {
        try {
            permissionsFile.writeText(json.encodeToString(perms))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
