package com.example.hermes.core.mobilecontrol

/**
 * Which kinds of external content the agent has read since the session started. Shown as a banner on the
 * confirmation of a send or a call, so the user knows the proposal may come from that text.
 */
class ExternalReads {
    private val seen = LinkedHashSet<String>()

    val kinds: Set<String> get() = seen.toSet()

    fun record(operation: String) {
        kindFor(operation)?.let { seen.add(it) }
    }

    fun reset() = seen.clear()

    companion object {
        private val SCREEN_OPERATIONS = setOf("observe", "click_element", "scroll", "set_text", "back", "launch_app", "tap_xy")

        /** The kind an operation's answer belongs to, or null when it returns no external content. */
        fun kindFor(operation: String): String? = when (operation) {
            in SCREEN_OPERATIONS -> "screen"
            "screenshot" -> Consent.SCREENSHOTS
            "calendar_read" -> Consent.CALENDAR
            "sms_read" -> Consent.SMS_READ
            "call_log_read" -> Consent.CALL_LOG_READ
            else -> null
        }
    }
}
