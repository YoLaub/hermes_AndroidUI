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

@Serializable
data class MobileScreenData(
    @SerialName("screen_revision") val screenRevision: String,
    @SerialName("package_name") val packageName: String,
    val title: String? = null,
    val elements: List<MobileElementInfo> = emptyList()
)

@Serializable
data class MobileCommandArguments(
    @SerialName("element_ref") val elementRef: String? = null,
    val text: String? = null,
    val direction: String? = null // "up", "down", "left", "right"
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
    val data: MobileScreenData? = null
)

@Serializable
data class MobileSessionStartMsg(
    val protocol: String = "mobile-control/1",
    val type: String = "session_start",
    @SerialName("session_id") val sessionId: String,
    @SerialName("target_package") val targetPackage: String,
    @SerialName("allowed_profile") val allowedProfile: String,
    val mode: String, // "observation", "interaction"
    @SerialName("duration_seconds") val durationSeconds: Int
)

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
    val expiresAt: Long
) {
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
