package com.example.hermes.core.mobilecontrol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MobileControlMode {
    @SerialName("observation")
    OBSERVATION,

    @SerialName("interaction")
    INTERACTION
}

@Serializable
enum class MobileCommandStatus {
    @SerialName("success")
    SUCCESS,

    @SerialName("rejected")
    REJECTED,

    @SerialName("error")
    ERROR
}

@Serializable
data class MobileElementInfo(
    @SerialName("element_ref") val elementRef: String,
    @SerialName("class_name") val className: String? = null,
    val text: String? = null,
    @SerialName("content_desc") val contentDesc: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val bounds: String? = null
)

/** A downscaled JPEG of the target app, base64. In memory only: never written to disk or logged. */
@Serializable
data class MobileScreenshot(
    @SerialName("mime_type") val mimeType: String,
    val width: Int,
    val height: Int,
    val data: String
)

/** One calendar event, reduced to what the user consented to share. Never stored or logged. */
@Serializable
data class MobileCalendarEvent(
    val title: String? = null,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val location: String? = null,
    @SerialName("all_day") val allDay: Boolean = false
)

@Serializable
data class MobileScreenData(
    @SerialName("screen_revision") val screenRevision: String,
    @SerialName("package_name") val packageName: String,
    val title: String? = null,
    val elements: List<MobileElementInfo> = emptyList(),
    val screenshot: MobileScreenshot? = null
)

@Serializable
data class MobileCommandArguments(
    @SerialName("element_ref") val elementRef: String? = null,
    val text: String? = null,
    val direction: String? = null, // "up", "down", "left", "right"
    /** Pixels of the last screenshot, for the tap_xy operation. */
    val x: Int? = null,
    val y: Int? = null,
    /** How many days ahead, for calendar_read (1..7). */
    val days: Int? = null
)

@Serializable
data class MobileCommand(
    val protocol: String = "mobile-control/1",
    val type: String = "command",
    @SerialName("command_id") val commandId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_id") val deviceId: String? = null,
    val operation: String, // "observe", "launch_app", "click_element", "scroll", "set_text", "back", "end_session"
    @SerialName("target_package") val targetPackage: String,
    @SerialName("screen_revision") val screenRevision: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val arguments: MobileCommandArguments? = null
)

@Serializable
data class MobileCommandResult(
    val protocol: String = "mobile-control/1",
    val type: String = "result",
    @SerialName("command_id") val commandId: String,
    val status: MobileCommandStatus,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("executed_at") val executedAt: Long? = null,
    val message: String? = null,
    val data: MobileScreenData? = null,
    /** Answer of calendar_read only. */
    @SerialName("calendar_events") val calendarEvents: List<MobileCalendarEvent>? = null
)

@Serializable
data class MobileSessionStartMsg(
    val protocol: String = "mobile-control/1",
    val type: String = "session_start",
    @SerialName("session_id") val sessionId: String,
    @SerialName("target_package") val targetPackage: String,
    @SerialName("allowed_profile") val allowedProfile: String,
    val mode: String, // "observation", "interaction"
    @SerialName("duration_seconds") val durationSeconds: Int,
    /** The user's explicit consent for this session. Off unless the user switched it on. */
    @SerialName("allow_screenshots") val allowScreenshots: Boolean = false,
    /** The user's explicit consent to let the agent read upcoming calendar events. Off unless switched on. */
    @SerialName("allow_calendar") val allowCalendar: Boolean = false,
    /** The consents by name (see [Consent]); the two booleans above stay for relays that predate this list. */
    val allow: List<String> = emptyList()
) {
    companion object {
        fun of(session: MobileControlSession) = MobileSessionStartMsg(
            sessionId = session.id,
            targetPackage = session.targetPackage,
            allowedProfile = session.allowedProfile,
            mode = session.mode.name.lowercase(),
            durationSeconds = session.durationSeconds,
            allowScreenshots = session.allowScreenshots,
            allowCalendar = session.allowCalendar,
            allow = session.consents.sorted()
        )
    }
}

@Serializable
data class MobileSessionEndMsg(
    val protocol: String = "mobile-control/1",
    val type: String = "session_end",
    @SerialName("session_id") val sessionId: String,
    val reason: String = "user_cancelled"
)

@Serializable
data class MobileAuthMsg(
    val protocol: String = "mobile-control/1",
    val type: String = "auth",
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String
)

/** Relay → phone: the session was registered. Only this makes a session "active". */
@Serializable
data class MobileSessionStartedAck(
    val protocol: String = "mobile-control/1",
    val type: String = "session_started_ack",
    @SerialName("session_id") val sessionId: String,
    val profile: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int? = null,
    @SerialName("allow_screenshots") val allowScreenshots: Boolean? = null,
    @SerialName("allow_calendar") val allowCalendar: Boolean? = null,
    val allow: List<String>? = null
) {
    fun echoedConsents(): Set<String> = Consent.echoed(allow, allowScreenshots, allowCalendar)
}

/** Relay → phone: the session was refused (never becomes active). */
@Serializable
data class MobileSessionError(
    val protocol: String = "mobile-control/1",
    val type: String = "session_error",
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("error_code") val errorCode: String,
    val message: String? = null
)

/** Relay → phone, right after auth_ok: what the relay itself holds for this device. */
@Serializable
data class MobileSessionState(
    val protocol: String = "mobile-control/1",
    val type: String = "session_state",
    val active: Boolean,
    @SerialName("session_id") val sessionId: String? = null,
    val profile: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("target_package") val targetPackage: String? = null,
    val mode: String? = null,
    @SerialName("allow_screenshots") val allowScreenshots: Boolean? = null,
    @SerialName("allow_calendar") val allowCalendar: Boolean? = null,
    val allow: List<String>? = null,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int? = null
) {
    fun echoedConsents(): Set<String> = Consent.echoed(allow, allowScreenshots, allowCalendar)
}

@Serializable
data class MobileAuthError(
    val protocol: String = "mobile-control/1",
    val type: String = "auth_error",
    @SerialName("error_code") val errorCode: String? = null,
    val message: String? = null
)

@Serializable
data class MobileDeviceStatusMsg(
    val protocol: String = "mobile-control/1",
    val type: String = "device_status",
    @SerialName("device_id") val deviceId: String,
    @SerialName("accessibility_enabled") val accessibilityEnabled: Boolean,
    @SerialName("has_active_session") val hasActiveSession: Boolean,
    @SerialName("target_package") val targetPackage: String? = null,
    @SerialName("allowed_profile") val allowedProfile: String? = null
)

@Serializable
data class MobileControlSession(
    val id: String,
    val targetPackage: String,
    val targetAppName: String,
    val allowedProfile: String,
    val mode: MobileControlMode,
    val startedAt: Long,
    val durationSeconds: Int,
    val expiresAt: Long,
    /** The consents the user gave for THIS session (never remembered across sessions). See [Consent]. */
    val consents: Set<String> = emptySet()
) {
    fun allows(consent: String): Boolean = consent in consents

    val allowScreenshots: Boolean
        get() = allows(Consent.SCREENSHOTS)

    val allowCalendar: Boolean
        get() = allows(Consent.CALENDAR)

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt

    val remainingSeconds: Long
        get() = maxOf(0L, (expiresAt - System.currentTimeMillis()) / 1000)
}

@Serializable
data class AllowedApp(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true
)

@Serializable
data class AuditLogEntry(
    val id: String,
    val timestamp: Long,
    val operation: String,
    val targetPackage: String,
    val status: String,
    val details: String? = null,
    val profile: String? = null
)
