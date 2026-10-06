package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class CommandSessionGuardTest {

    private val now = 1_000_000L

    private fun session(
        id: String = "ses_1",
        mode: MobileControlMode = MobileControlMode.INTERACTION,
        expiresAt: Long = now + 600_000L,
        allowScreenshots: Boolean = false,
        allowCalendar: Boolean = false,
        extra: Set<String> = emptySet()
    ) = MobileControlSession(
        id = id, targetPackage = "com.linkedin.android", targetAppName = "LinkedIn",
        allowedProfile = "john", mode = mode, startedAt = now, durationSeconds = 900, expiresAt = expiresAt,
        consents = buildSet {
            if (allowScreenshots) add(Consent.SCREENSHOTS)
            if (allowCalendar) add(Consent.CALENDAR)
            addAll(extra)
        }
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

    // ── Screenshots: the user's consent for this session, enforced on the phone too ──

    @Test
    fun withoutConsentScreenshotAndTapByCoordinatesAreRefusedOnThePhoneToo() {
        for (op in listOf("screenshot", "tap_xy")) {
            val r = check(session(), cmd(op))!!
            assertEquals(op, "SCREENSHOTS_NOT_ALLOWED", r.code)
            assertTrue(r.message.contains("téléphone", ignoreCase = true))
        }
    }

    @Test
    fun withConsentBothAreAccepted() {
        val s = session(allowScreenshots = true)
        assertNull(check(s, cmd("screenshot")))
        assertNull(check(s, cmd("tap_xy")))
    }

    @Test
    fun observationModeAllowsAConsentedScreenshotButNeverATap() {
        val obs = session(mode = MobileControlMode.OBSERVATION, allowScreenshots = true)
        assertNull(check(obs, cmd("screenshot")))
        assertEquals("MODE_DENIED", check(obs, cmd("tap_xy"))!!.code)
    }

    @Test
    fun consentDoesNotOpenAnythingElseInObservationMode() {
        val obs = session(mode = MobileControlMode.OBSERVATION, allowScreenshots = true)
        for (op in listOf("click_element", "set_text", "scroll", "launch_app", "back")) {
            assertEquals(op, "MODE_DENIED", check(obs, cmd(op))!!.code)
        }
    }

    @Test
    fun theSessionAndPackageAreStillCheckedBeforeConsent() {
        assertEquals("SESSION_NOT_ON_PHONE", check(null, cmd("screenshot"))!!.code)
        assertEquals("APP_NOT_ALLOWED", check(session(allowScreenshots = true), cmd("screenshot", pkg = "com.other"))!!.code)
    }

    // ── Calendar: its own consent, never implied by the screenshot consent ──

    @Test
    fun withoutCalendarConsentTheReadIsRefusedOnThePhoneEvenWithScreenshotConsent() {
        val r = check(session(allowScreenshots = true), cmd("calendar_read"))!!
        assertEquals("CALENDAR_NOT_ALLOWED", r.code)
        assertTrue(r.message.contains("téléphone", ignoreCase = true))
    }

    @Test
    fun calendarConsentAllowsTheReadAndNothingElse() {
        val s = session(allowCalendar = true)
        assertNull(check(s, cmd("calendar_read")))
        assertEquals("SCREENSHOTS_NOT_ALLOWED", check(s, cmd("screenshot"))!!.code)
    }

    @Test
    fun aConsentedCalendarReadIsAllowedInObservationMode() {
        val obs = session(mode = MobileControlMode.OBSERVATION, allowCalendar = true)
        assertNull(check(obs, cmd("calendar_read")))
    }

    // ── Messages and call log: each its own consent, read-only so allowed in observation mode ──

    @Test
    fun withoutTheirConsentSmsAndCallLogReadsAreRefusedOnThePhone() {
        val all = session(allowScreenshots = true, allowCalendar = true)
        assertEquals("SMS_NOT_ALLOWED", check(all, cmd("sms_read"))!!.code)
        assertEquals("CALL_LOG_NOT_ALLOWED", check(all, cmd("call_log_read"))!!.code)
        assertTrue(check(all, cmd("sms_read"))!!.message.contains("téléphone", ignoreCase = true))
    }

    @Test
    fun eachConsentOpensOnlyItsOwnRead() {
        val sms = session(extra = setOf(Consent.SMS_READ))
        assertNull(check(sms, cmd("sms_read")))
        assertEquals("CALL_LOG_NOT_ALLOWED", check(sms, cmd("call_log_read"))!!.code)
        val calls = session(extra = setOf(Consent.CALL_LOG_READ))
        assertNull(check(calls, cmd("call_log_read")))
        assertEquals("SMS_NOT_ALLOWED", check(calls, cmd("sms_read"))!!.code)
    }

    @Test
    fun consentedReadsAreAllowedInObservationMode() {
        val obs = session(mode = MobileControlMode.OBSERVATION, extra = setOf(Consent.SMS_READ, Consent.CALL_LOG_READ))
        assertNull(check(obs, cmd("sms_read")))
        assertNull(check(obs, cmd("call_log_read")))
    }

    // ── Irreversible actions: own consent, interaction mode only ──

    @Test
    fun withoutTheirConsentSendAndCallAreRefusedOnThePhone() {
        val reads = session(allowScreenshots = true, allowCalendar = true,
            extra = setOf(Consent.SMS_READ, Consent.CALL_LOG_READ))
        assertEquals("SMS_SEND_NOT_ALLOWED", check(reads, cmd("sms_send"))!!.code)
        assertEquals("CALL_NOT_ALLOWED", check(reads, cmd("call_place"))!!.code)
    }

    @Test
    fun eachActionConsentOpensOnlyItsOwnAction() {
        val send = session(extra = setOf(Consent.SMS_SEND))
        assertNull(check(send, cmd("sms_send")))
        assertEquals("CALL_NOT_ALLOWED", check(send, cmd("call_place"))!!.code)
    }

    @Test
    fun actionsAreRefusedInObservationModeEvenWithConsent() {
        val obs = session(mode = MobileControlMode.OBSERVATION, extra = setOf(Consent.SMS_SEND, Consent.CALL_PLACE))
        assertEquals("MODE_DENIED", check(obs, cmd("sms_send"))!!.code)
        assertEquals("MODE_DENIED", check(obs, cmd("call_place"))!!.code)
    }

    // ── Calendar write: own consent, interaction mode only ──

    @Test
    fun readingTheCalendarDoesNotOpenWritingAndWritingNeedsItsOwnConsent() {
        val reads = session(allowCalendar = true, allowScreenshots = true)
        for (op in listOf("calendar_create", "calendar_update", "calendar_delete")) {
            assertEquals(op, "CALENDAR_WRITE_NOT_ALLOWED", check(reads, cmd(op))!!.code)
        }
    }

    @Test
    fun theWriteConsentOpensTheThreeChangesButNotReading() {
        val w = session(extra = setOf(Consent.CALENDAR_WRITE))
        for (op in listOf("calendar_create", "calendar_update", "calendar_delete")) assertNull(op, check(w, cmd(op)))
        assertEquals("CALENDAR_NOT_ALLOWED", check(w, cmd("calendar_read"))!!.code)
    }

    @Test
    fun calendarChangesAreRefusedInObservationModeEvenWithConsent() {
        val obs = session(mode = MobileControlMode.OBSERVATION, extra = setOf(Consent.CALENDAR_WRITE))
        for (op in listOf("calendar_create", "calendar_update", "calendar_delete")) {
            assertEquals(op, "MODE_DENIED", check(obs, cmd(op))!!.code)
        }
    }
}
