package com.example.hermes.core.mobilecontrol

/** Target package of a session that is not bound to one app (the standing session of an agent phone). */
const val ANY_APP = "*"

/**
 * What this build of the app trusts. FULL is the strict one (a session the user starts, consents, and a tap to
 * confirm anything irreversible). LITE is for a phone dedicated to the agent: the relay opens the session, its
 * consents are trusted as sent, and nothing asks for a confirmation. FULL is the default everywhere.
 */
data class ControlPolicy(
    val trustRelaySession: Boolean,
    val requireConfirmation: Boolean,
    /** Keep the foreground service (and so the relay connection) alive even with no session. */
    val keepAlive: Boolean
) {
    companion object {
        val FULL = ControlPolicy(trustRelaySession = false, requireConfirmation = true, keepAlive = false)
        val LITE = ControlPolicy(trustRelaySession = true, requireConfirmation = false, keepAlive = true)
    }
}
