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
}
