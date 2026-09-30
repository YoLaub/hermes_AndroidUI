package com.example.hermes.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class EventStatus {
    CONFIRMED,
    PENDING_AGENT,
    COMPLETED,
    CANCELLED
}

@Serializable
enum class CalendarPermissionLevel {
    READ_ONLY,
    READ_WRITE,
    ADMIN
}

@Serializable
data class ProfileCalendarPermission(
    val profileName: String,
    val role: String, // "user" or "agent"
    val permission: CalendarPermissionLevel = CalendarPermissionLevel.READ_WRITE,
    val colorHex: String = "#58A6FF"
)

@Serializable
data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val dateEpochDay: Long, // LocalDate.toEpochDay()
    val startTime: String, // e.g. "09:30"
    val endTime: String, // e.g. "10:30"
    val organizer: String = "Yoann", // Who created / owns it
    val assignedProfiles: List<String> = listOf("Yoann"), // Involved agents/users
    val status: EventStatus = EventStatus.CONFIRMED,
    val locationOrLink: String = "",
    val isAllDay: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
