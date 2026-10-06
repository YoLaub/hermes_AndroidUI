package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class ForegroundPolicyTest {

    private fun session(id: String = "ses_1") = MobileControlSession(
        id = id, targetPackage = "com.linkedin.android", targetAppName = "LinkedIn", allowedProfile = "john",
        mode = MobileControlMode.INTERACTION, startedAt = 0L, durationSeconds = 900, expiresAt = 900_000L
    )

    @Test
    fun noSessionNeedsNoForegroundService() {
        assertFalse(ForegroundPolicy.shouldRunForeground(SessionView(pending = null, active = null)))
    }

    @Test
    fun anActiveSessionKeepsTheChannelAliveInTheForeground() {
        assertTrue(ForegroundPolicy.shouldRunForeground(SessionView(pending = null, active = session())))
    }

    @Test
    fun aPendingStartAlsoKeepsItAliveSoTheRelayConfirmationCanArrive() {
        assertTrue(ForegroundPolicy.shouldRunForeground(SessionView(pending = session(), active = null)))
    }

    @Test
    fun activeSessionNotificationNamesTheAppProfileAndMode() {
        val c = ForegroundPolicy.notificationFor(SessionView(pending = null, active = session()))
        assertTrue(c.title.contains("LinkedIn"))
        assertTrue(c.text.contains("john") && c.text.contains("interaction"))
    }

    @Test
    fun pendingSessionNotificationSaysItIsWaitingForTheRelay() {
        val c = ForegroundPolicy.notificationFor(SessionView(pending = session(), active = null))
        assertTrue(c.text.contains("relais", ignoreCase = true))
    }

    @Test
    fun thereIsAlwaysSomethingToShowEvenWhenNoSessionRemains() {
        // startForegroundService() obliges startForeground() even if the session ended meanwhile.
        val c = ForegroundPolicy.notificationFor(SessionView(pending = null, active = null))
        assertTrue(c.title.isNotBlank() && c.text.isNotBlank())
    }

    @Test
    fun theNotificationSaysWhenScreenshotsAreAllowed() {
        val on = ForegroundPolicy.notificationFor(SessionView(pending = null, active = session().copy(consents = setOf(Consent.SCREENSHOTS))))
        assertTrue(on.text.contains("captures", ignoreCase = true))
        val off = ForegroundPolicy.notificationFor(SessionView(pending = null, active = session()))
        assertFalse(off.text.contains("captures", ignoreCase = true))
    }
}
