package com.example.hermes.core.mobilecontrol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ConsentTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val now = 1_000_000L

    @Test
    fun operationsMapToTheConsentTheyNeed() {
        assertEquals(Consent.SCREENSHOTS, Consent.requiredFor("screenshot"))
        assertEquals(Consent.SCREENSHOTS, Consent.requiredFor("tap_xy"))
        assertEquals(Consent.CALENDAR, Consent.requiredFor("calendar_read"))
        assertNull(Consent.requiredFor("observe"))
        assertNull(Consent.requiredFor("click_element"))
    }

    @Test
    fun everyConsentHasItsOwnRefusalCode() {
        assertEquals("SCREENSHOTS_NOT_ALLOWED", Consent.refusalCode(Consent.SCREENSHOTS))
        assertEquals("CALENDAR_NOT_ALLOWED", Consent.refusalCode(Consent.CALENDAR))
    }

    @Test
    fun theWireListWinsOverTheLegacyBooleans() {
        assertEquals(setOf(Consent.CALENDAR), Consent.echoed(listOf("calendar"), screenshotsLegacy = true, calendarLegacy = false))
    }

    @Test
    fun withoutAListTheLegacyBooleansAreRead() {
        assertEquals(setOf(Consent.SCREENSHOTS, Consent.CALENDAR), Consent.echoed(null, true, true))
        assertEquals(setOf(Consent.SCREENSHOTS), Consent.echoed(null, true, null))
        assertEquals(emptySet<String>(), Consent.echoed(null, null, null))
        assertEquals(emptySet<String>(), Consent.echoed(null, false, false))
    }

    @Test
    fun unknownNamesAreDropped() {
        assertEquals(setOf(Consent.CALENDAR), Consent.echoed(listOf("calendar", "launch_missiles", ""), null, null))
    }

    // ── The session and the wire ──

    private fun session(consents: Set<String> = emptySet()) = MobileControlSession(
        id = "ses_1", targetPackage = "p", targetAppName = "P", allowedProfile = "john",
        mode = MobileControlMode.INTERACTION, startedAt = now, durationSeconds = 900,
        expiresAt = now + 900_000L, consents = consents
    )

    @Test
    fun theConvenienceFlagsReadTheConsentSet() {
        val s = session(setOf(Consent.CALENDAR))
        assertTrue(s.allowCalendar)
        assertFalse(s.allowScreenshots)
        assertTrue(s.allows(Consent.CALENDAR))
    }

    @Test
    fun sessionStartCarriesTheSortedListAndTheLegacyBooleans() {
        val msg = MobileSessionStartMsg.of(session(setOf(Consent.CALENDAR, Consent.SCREENSHOTS)))
        assertEquals(listOf("calendar", "screenshots"), msg.allow)
        val text = json.encodeToString(msg)
        assertTrue(text.contains("\"allow\":[\"calendar\",\"screenshots\"]"))
        assertTrue(text.contains("\"allow_screenshots\":true") && text.contains("\"allow_calendar\":true"))
        assertEquals(emptyList<String>(), MobileSessionStartMsg.of(session()).allow)
    }

    @Test
    fun theAckAndTheStateExposeTheirEchoedConsents() {
        val ack = MobileSessionStartedAck(sessionId = "s", allow = listOf("calendar"))
        assertEquals(setOf(Consent.CALENDAR), ack.echoedConsents())
        val state = MobileSessionState(active = true, allowScreenshots = true)
        assertEquals(setOf(Consent.SCREENSHOTS), state.echoedConsents())
    }

    @Test
    fun theEffectiveConsentIsTheIntersectionOfThePhoneAndTheRelayList() {
        val pending = session(setOf(Consent.CALENDAR, Consent.SCREENSHOTS))
        val t = SessionConfirmation.onAck(
            SessionView(pending = pending, active = null),
            MobileSessionStartedAck(sessionId = "ses_1", allow = listOf("calendar"), expiresInSeconds = 60), now
        )
        assertEquals(setOf(Consent.CALENDAR), t.view.active!!.consents)
    }

    @Test
    fun theRelayListCanNeverGrantWhatThePhoneDidNotGive() {
        val t = SessionConfirmation.onAck(
            SessionView(pending = session(emptySet()), active = null),
            MobileSessionStartedAck(sessionId = "ses_1", allow = listOf("calendar", "screenshots")), now
        )
        assertTrue(t.view.active!!.consents.isEmpty())
    }

    @Test
    fun aSessionAdoptedFromTheRelayHasNoConsentWhateverTheListSays() {
        val t = SessionConfirmation.onServerState(
            SessionView(null, null),
            MobileSessionState(active = true, sessionId = "ses_9", profile = "john", mode = "interaction",
                allow = listOf("calendar", "screenshots"), expiresInSeconds = 100), now
        )
        assertTrue(t.view.active!!.consents.isEmpty())
    }

    // ── Messages and call log: one consent each, never implied by another ──

    @Test
    fun messageAndCallLogOperationsMapToTheirOwnConsent() {
        assertEquals(Consent.SMS_READ, Consent.requiredFor("sms_read"))
        assertEquals(Consent.CALL_LOG_READ, Consent.requiredFor("call_log_read"))
        assertEquals("SMS_NOT_ALLOWED", Consent.refusalCode(Consent.SMS_READ))
        assertEquals("CALL_LOG_NOT_ALLOWED", Consent.refusalCode(Consent.CALL_LOG_READ))
    }

    @Test
    fun theNewNamesAreKnownAndTravelInTheList() {
        assertEquals(
            setOf(Consent.SMS_READ, Consent.CALL_LOG_READ),
            Consent.echoed(listOf("sms_read", "call_log_read", "nope"), null, null)
        )
    }

    @Test
    fun eachNewConsentNeedsItsOwnAndroidPermissionAndContactsAreOptional() {
        assertEquals(listOf("android.permission.READ_SMS"), Consent.PERMISSIONS[Consent.SMS_READ])
        assertEquals(listOf("android.permission.READ_CALL_LOG"), Consent.PERMISSIONS[Consent.CALL_LOG_READ])
    }

    // ── Irreversible actions: their own consents, permissions and codes ──

    @Test
    fun sendAndCallMapToTheirOwnConsentAndNothingElseOpensThem() {
        assertEquals(Consent.SMS_SEND, Consent.requiredFor("sms_send"))
        assertEquals(Consent.CALL_PLACE, Consent.requiredFor("call_place"))
        assertEquals("SMS_SEND_NOT_ALLOWED", Consent.refusalCode(Consent.SMS_SEND))
        assertEquals("CALL_NOT_ALLOWED", Consent.refusalCode(Consent.CALL_PLACE))
        assertTrue(Consent.SMS_SEND in Consent.KNOWN && Consent.CALL_PLACE in Consent.KNOWN)
    }

    @Test
    fun actionsNeedTheirPermissionAndTheContactsOnes() {
        assertEquals(
            listOf("android.permission.SEND_SMS", "android.permission.READ_CONTACTS"),
            Consent.PERMISSIONS[Consent.SMS_SEND]
        )
        assertEquals(
            listOf("android.permission.CALL_PHONE", "android.permission.READ_CONTACTS"),
            Consent.PERMISSIONS[Consent.CALL_PLACE]
        )
    }
}
