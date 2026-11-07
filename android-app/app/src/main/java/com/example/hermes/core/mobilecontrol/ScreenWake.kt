package com.example.hermes.core.mobilecontrol

/** What to do with the screen before a command runs. [blockedCode] is a normalized relay error code. */
data class WakePlan(val wake: Boolean, val swipeToUnlock: Boolean, val blockedCode: String?) {
    companion object {
        val NOTHING = WakePlan(wake = false, swipeToUnlock = false, blockedCode = null)
    }
}

/**
 * A phone dedicated to the agent sleeps between requests. When a command needs the screen, wake it and swipe
 * away a plain "swipe to unlock" keyguard. A PIN, pattern or fingerprint is never bypassed: the command is
 * refused as DEVICE_LOCKED (the screen still wakes, so the owner sees the phone is waiting).
 */
object ScreenWake {
    /** Reading data or placing a call/SMS does not look at the screen. */
    private val NO_SCREEN = setOf(
        "calendar_read", "sms_read", "call_log_read", "sms_send", "call_place",
        "calendar_create", "calendar_update", "calendar_delete", "end_session"
    )

    fun operationNeedsScreen(operation: String): Boolean = operation !in NO_SCREEN

    fun plan(
        wakesScreen: Boolean,
        operation: String,
        interactive: Boolean,
        keyguardLocked: Boolean,
        keyguardSecure: Boolean
    ): WakePlan {
        if (!wakesScreen || !operationNeedsScreen(operation)) return WakePlan.NOTHING
        val wake = !interactive
        if (!keyguardLocked) return WakePlan(wake = wake, swipeToUnlock = false, blockedCode = null)
        return if (keyguardSecure) {
            WakePlan(wake = wake, swipeToUnlock = false, blockedCode = "DEVICE_LOCKED")
        } else {
            WakePlan(wake = wake, swipeToUnlock = true, blockedCode = null)
        }
    }
}
