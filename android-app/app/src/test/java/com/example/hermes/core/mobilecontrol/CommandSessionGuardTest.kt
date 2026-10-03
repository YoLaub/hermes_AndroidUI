package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class CommandSessionGuardTest {

    private val now = 1_000_000L

    private fun session(
        id: String = "ses_1",
        mode: MobileControlMode = MobileControlMode.INTERACTION,
        expiresAt: Long = now + 600_000L
    ) = MobileControlSession(
        id = id, targetPackage = "com.linkedin.android", targetAppName = "LinkedIn",
        allowedProfile = "john", mode = mode, startedAt = now, durationSeconds = 900, expiresAt = expiresAt
    )

    private fun cmd(op: String = "observe", sessionId: String = "ses_1", pkg: String = "com.linkedin.android") =
        MobileCommand(commandId = "cmd_1", sessionId = sessionId, operation = op, targetPackage = pkg)

    private fun check(
        active: MobileControlSession?,
        c: MobileCommand,
        pendingId: String? = null
    ) = CommandSessionGuard.validate(active, pendingId, c, now)

    @Test
    fun matchingSessionIsAccepted() {
        assertNull(check(session(), cmd()))
    }

    @Test
    fun noSessionOnThePhoneIsItsOwnCodeAndNamesThePhone() {
        val r = check(null, cmd())!!
        assertEquals("SESSION_NOT_ON_PHONE", r.code)
        assertTrue(r.message.contains("téléphone", ignoreCase = true))
    }

    @Test
    fun anotherSessionOnThePhoneIsDistinctFromNoSession() {
        val r = check(session("ses_other"), cmd(sessionId = "ses_1"))!!
        assertEquals("SESSION_ID_MISMATCH", r.code)
        assertTrue(r.message.contains("ses_other") && r.message.contains("ses_1"))
    }

    @Test
    fun aStartWaitingForTheRelayConfirmationIsNotReportedAsAbsent() {
        val r = check(null, cmd(sessionId = "ses_1"), pendingId = "ses_1")!!
        assertEquals("SESSION_PENDING_ON_PHONE", r.code)
    }

    @Test
    fun aPendingSessionWithAnotherIdIsStillAbsent() {
        val r = check(null, cmd(sessionId = "ses_1"), pendingId = "ses_zzz")!!
        assertEquals("SESSION_NOT_ON_PHONE", r.code)
    }

    @Test
    fun expiredSessionIsReported() {
        val r = check(session(expiresAt = now - 1), cmd())!!
        assertEquals("SESSION_EXPIRED", r.code)
    }

    @Test
    fun wrongPackageIsRefused() {
        val r = check(session(), cmd(pkg = "com.other.app"))!!
        assertEquals("APP_NOT_ALLOWED", r.code)
    }

    @Test
    fun observationModeAllowsObserveAndEndSession() {
        val obs = session(mode = MobileControlMode.OBSERVATION)
        assertNull(check(obs, cmd("observe")))
        assertNull(check(obs, cmd("end_session")))
    }

    @Test
    fun observationModeRefusesEveryInteraction() {
        val obs = session(mode = MobileControlMode.OBSERVATION)
        for (op in listOf("click_element", "set_text", "scroll", "launch_app", "back")) {
            assertEquals(op, "MODE_DENIED", check(obs, cmd(op))!!.code)
        }
    }

    @Test
    fun interactionModeAllowsInteractions() {
        for (op in listOf("observe", "click_element", "set_text", "scroll", "launch_app", "back", "end_session")) {
            assertNull(op, check(session(), cmd(op)))
        }
    }

    @Test
    fun theSessionIsCheckedBeforeTheMode() {
        // With no session at all the answer is about the session, not about the mode.
        assertEquals("SESSION_NOT_ON_PHONE", check(null, cmd("click_element"))!!.code)
    }
}
