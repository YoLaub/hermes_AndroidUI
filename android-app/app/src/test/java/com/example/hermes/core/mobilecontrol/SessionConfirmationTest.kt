package com.example.hermes.core.mobilecontrol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SessionConfirmationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val now = 1_000_000L

    private fun session(id: String = "ses_1") = MobileControlSession(
        id = id,
        targetPackage = "com.linkedin.android",
        targetAppName = "LinkedIn",
        allowedProfile = "john",
        mode = MobileControlMode.INTERACTION,
        startedAt = now,
        durationSeconds = 900,
        expiresAt = now + 900_000L
    )

    private fun ack(id: String = "ses_1", expiresIn: Int? = 600) =
        MobileSessionStartedAck(sessionId = id, profile = "john", deviceId = "dev_1", expiresInSeconds = expiresIn)

    // ── A started session is only "pending" until the relay confirms it ──────

    @Test
    fun ackPromotesTheMatchingPendingSessionToActive() {
        val view = SessionView(pending = session(), active = null)
        val t = SessionConfirmation.onAck(view, ack(), now)
        assertNull(t.view.pending)
        assertEquals("ses_1", t.view.active?.id)
        // The relay's expiry wins over the phone's own estimate.
        assertEquals(now + 600_000L, t.view.active?.expiresAt)
    }

    @Test
    fun ackForAnotherSessionIsIgnored() {
        val view = SessionView(pending = session("ses_1"), active = null)
        val t = SessionConfirmation.onAck(view, ack("ses_other"), now)
        assertEquals("ses_1", t.view.pending?.id)
        assertNull(t.view.active)
    }

    @Test
    fun errorClearsPendingAndExplainsWhyWithoutEverBecomingActive() {
        val view = SessionView(pending = session(), active = null)
        val t = SessionConfirmation.onError(view, MobileSessionError(sessionId = "ses_1", errorCode = "DEVICE_NOT_AUTHENTICATED"))
        assertNull(t.view.pending)
        assertNull(t.view.active)
        assertTrue(t.notice!!.contains("appair", ignoreCase = true))
    }

    @Test
    fun profileRefusalIsExplained() {
        val view = SessionView(pending = session(), active = null)
        val t = SessionConfirmation.onError(view, MobileSessionError(sessionId = "ses_1", errorCode = "PROFILE_NOT_ALLOWED"))
        assertNull(t.view.active)
        assertTrue(t.notice!!.contains("john", ignoreCase = true))
    }

    @Test
    fun timeoutWithoutConfirmationFailsThePendingSession() {
        val view = SessionView(pending = session(), active = null)
        val t = SessionConfirmation.onTimeout(view, "ses_1")
        assertNull(t.view.pending)
        assertNull(t.view.active)
        assertNotNull(t.notice)
    }

    @Test
    fun timeoutAfterConfirmationChangesNothing() {
        val view = SessionView(pending = null, active = session())
        val t = SessionConfirmation.onTimeout(view, "ses_1")
        assertEquals("ses_1", t.view.active?.id)
        assertNull(t.notice)
    }

    // ── Reconnection: the relay's view wins ──────────────────────────────────

    @Test
    fun serverSayingNoSessionEndsTheLocalActiveSession() {
        val view = SessionView(pending = null, active = session())
        val t = SessionConfirmation.onServerState(view, MobileSessionState(active = false), now)
        assertNull(t.view.active)
        assertEquals("ses_1", t.endedLocally?.id)
        assertNotNull(t.notice)
    }

    @Test
    fun serverConfirmingTheSameSessionKeepsItAndRefreshesExpiry() {
        val view = SessionView(pending = null, active = session())
        val t = SessionConfirmation.onServerState(
            view, MobileSessionState(active = true, sessionId = "ses_1", profile = "john", expiresInSeconds = 120), now
        )
        assertEquals("ses_1", t.view.active?.id)
        assertEquals(now + 120_000L, t.view.active?.expiresAt)
        assertNull(t.endedLocally)
    }

    @Test
    fun serverStateCanConfirmAPendingSession() {
        val view = SessionView(pending = session(), active = null)
        val t = SessionConfirmation.onServerState(
            view, MobileSessionState(active = true, sessionId = "ses_1", profile = "john", expiresInSeconds = 300), now
        )
        assertNull(t.view.pending)
        assertEquals("ses_1", t.view.active?.id)
    }

    @Test
    fun aSessionTheServerHoldsButThePhoneDoesNotKnowIsAdoptedSoTheUserCanSeeAndStopIt() {
        val view = SessionView(pending = null, active = null)
        val t = SessionConfirmation.onServerState(
            view,
            MobileSessionState(
                active = true, sessionId = "ses_ghost", profile = "john",
                targetPackage = "com.linkedin.android", mode = "interaction", expiresInSeconds = 200
            ),
            now
        )
        assertEquals("ses_ghost", t.view.active?.id)
        assertEquals("john", t.view.active?.allowedProfile)
        assertNotNull(t.notice)
    }

    @Test
    fun inactiveServerStateLeavesAPendingStartAlone() {
        val view = SessionView(pending = session(), active = null)
        val t = SessionConfirmation.onServerState(view, MobileSessionState(active = false), now)
        assertEquals("ses_1", t.view.pending?.id)
    }

    // ── Wire format of the relay's new messages ──────────────────────────────

    @Test
    fun relayMessagesDecode() {
        val a = json.decodeFromString<MobileSessionStartedAck>(
            """{"protocol":"mobile-control/1","type":"session_started_ack","session_id":"ses_9","profile":"john","device_id":"dev_1","expires_in_seconds":900}"""
        )
        assertEquals("ses_9", a.sessionId)
        assertEquals(900, a.expiresInSeconds)

        val e = json.decodeFromString<MobileSessionError>(
            """{"type":"session_error","session_id":"ses_9","error_code":"PROFILE_NOT_ALLOWED","message":"x"}"""
        )
        assertEquals("PROFILE_NOT_ALLOWED", e.errorCode)

        val s = json.decodeFromString<MobileSessionState>(
            """{"type":"session_state","active":true,"session_id":"ses_9","profile":"john","target_package":"com.linkedin.android","mode":"interaction","expires_in_seconds":50}"""
        )
        assertTrue(s.active)
        assertEquals("com.linkedin.android", s.targetPackage)

        val authErr = json.decodeFromString<MobileAuthError>(
            """{"type":"auth_error","error_code":"DEVICE_AUTH_FAILED","message":"Identifiants invalides."}"""
        )
        assertEquals("DEVICE_AUTH_FAILED", authErr.errorCode)
    }

    @Test
    fun legacyAckWithOnlyASessionIdStillDecodes() {
        val a = json.decodeFromString<MobileSessionStartedAck>(
            """{"protocol":"mobile-control/1","type":"session_started_ack","session_id":"ses_old"}"""
        )
        assertEquals("ses_old", a.sessionId)
        assertNull(a.expiresInSeconds)
    }

    // ── Screenshot consent: BOTH the user (on this phone) and the relay must say yes ──

    private fun sessionWithConsent(consent: Boolean) = session().copy(consents = if (consent) setOf(Consent.SCREENSHOTS) else emptySet())

    @Test
    fun theRelayCanRemoveTheConsentButNeverGrantIt() {
        for (local in listOf(true, false)) for (relay in listOf(true, false, null)) {
            val view = SessionView(pending = sessionWithConsent(local), active = null)
            val t = SessionConfirmation.onAck(view, ack().copy(allowScreenshots = relay), now)
            assertEquals("local=$local relay=$relay", local && relay == true, t.view.active!!.allowScreenshots)
        }
    }

    @Test
    fun aRelayAnsweringYesToASessionTheUserStartedWithoutConsentChangesNothing() {
        val view = SessionView(pending = sessionWithConsent(false), active = null)
        assertFalse(SessionConfirmation.onAck(view, ack().copy(allowScreenshots = true), now).view.active!!.allowScreenshots)
    }

    @Test
    fun anOlderRelayThatSaysNothingDoesNotGrantConsentTheUserGaveItCannotEnforce() {
        val on = SessionView(pending = sessionWithConsent(true), active = null)
        assertFalse(SessionConfirmation.onAck(on, ack(), now).view.active!!.allowScreenshots)
    }

    @Test
    fun aSessionAdoptedFromTheRelayNeverHasScreenshotsWhateverTheRelaySays() {
        val none = SessionView(pending = null, active = null)
        for (relay in listOf(true, false, null)) {
            val t = SessionConfirmation.onServerState(
                none, MobileSessionState(active = true, sessionId = "ses_x", profile = "john",
                    targetPackage = "com.linkedin.android", mode = "interaction", allowScreenshots = relay, expiresInSeconds = 100), now
            )
            assertFalse("relay=$relay", t.view.active!!.allowScreenshots)
        }
    }

    @Test
    fun theRelaysStateAlsoCannotGrantConsentToAKnownSession() {
        for (local in listOf(true, false)) for (relay in listOf(true, false, null)) {
            val view = SessionView(pending = null, active = sessionWithConsent(local))
            val t = SessionConfirmation.onServerState(
                view, MobileSessionState(active = true, sessionId = "ses_1", profile = "john", allowScreenshots = relay, expiresInSeconds = 60), now
            )
            assertEquals("local=$local relay=$relay", local && relay == true, t.view.active!!.allowScreenshots)
        }
    }

    @Test
    fun aPendingSessionConfirmedByTheRelaysStateFollowsTheSameRule() {
        for (local in listOf(true, false)) for (relay in listOf(true, false)) {
            val view = SessionView(pending = sessionWithConsent(local), active = null)
            val t = SessionConfirmation.onServerState(
                view, MobileSessionState(active = true, sessionId = "ses_1", profile = "john", allowScreenshots = relay, expiresInSeconds = 60), now
            )
            assertEquals("local=$local relay=$relay", local && relay, t.view.active!!.allowScreenshots)
        }
    }

    @Test
    fun theConsentTravelsInTheSessionStartMessage() {
        val msg = MobileSessionStartMsg(
            sessionId = "ses_1", targetPackage = "com.linkedin.android", allowedProfile = "john",
            mode = "interaction", durationSeconds = 900
        )
        assertFalse(msg.allowScreenshots)   // default: off
        val encoded = json.encodeToString(msg.copy(allowScreenshots = true))
        assertTrue(encoded.contains("\"allow_screenshots\":true"))
        assertTrue(json.encodeToString(msg).contains("\"allow_screenshots\":false"))
    }

    @Test
    fun theScreenshotResultAndTheCoordinateArgumentsDecode() {
        val r = json.decodeFromString<MobileCommandResult>(
            """{"command_id":"c1","status":"success","data":{"screen_revision":"rev_9","package_name":"p","elements":[],
               "screenshot":{"mime_type":"image/jpeg","width":576,"height":1280,"data":"QUJD"}}}"""
        )
        assertEquals(576, r.data!!.screenshot!!.width)
        assertEquals("image/jpeg", r.data!!.screenshot!!.mimeType)
        val c = json.decodeFromString<MobileCommand>(
            """{"command_id":"c2","session_id":"s","operation":"tap_xy","target_package":"p","screen_revision":"rev_9",
               "arguments":{"x":120,"y":340}}"""
        )
        assertEquals(120, c.arguments!!.x); assertEquals(340, c.arguments!!.y)
    }

    // ── Calendar consent follows the same rule as screenshots: phone AND relay, never granted by the relay ──

    @Test
    fun calendarConsentIsThePhoneChoiceAndTheRelayEcho() {
        for (local in listOf(true, false)) for (relay in listOf(true, false, null)) {
            val view = SessionView(pending = session().copy(consents = if (local) setOf(Consent.CALENDAR) else emptySet()), active = null)
            val t = SessionConfirmation.onAck(view, ack().copy(allowCalendar = relay), now)
            assertEquals("local=$local relay=$relay", local && relay == true, t.view.active!!.allowCalendar)
        }
    }

    @Test
    fun calendarConsentIsIndependentFromScreenshotConsent() {
        val view = SessionView(pending = session().copy(consents = setOf(Consent.CALENDAR)), active = null)
        val t = SessionConfirmation.onAck(view, ack().copy(allowScreenshots = true, allowCalendar = true), now)
        assertTrue(t.view.active!!.allowCalendar)
        assertFalse(t.view.active!!.allowScreenshots)
    }

    @Test
    fun aSessionAdoptedFromTheRelayNeverHasCalendarAccess() {
        val t = SessionConfirmation.onServerState(
            SessionView(null, null),
            MobileSessionState(active = true, sessionId = "ses_9", profile = "john",
                targetPackage = "com.linkedin.android", mode = "interaction", allowCalendar = true, expiresInSeconds = 100), now
        )
        assertFalse(t.view.active!!.allowCalendar)
    }

    @Test
    fun serverStateCannotGrantTheCalendarEither() {
        val view = SessionView(null, session().copy(consents = emptySet()))
        val t = SessionConfirmation.onServerState(
            view, MobileSessionState(active = true, sessionId = "ses_1", profile = "john", allowCalendar = true, expiresInSeconds = 60), now)
        assertFalse(t.view.active!!.allowCalendar)
    }

    @Test
    fun sessionStartCarriesTheCalendarConsentOffByDefault() {
        val msg = MobileSessionStartMsg(sessionId = "s", targetPackage = "p", allowedProfile = "john", mode = "interaction", durationSeconds = 60)
        assertFalse(msg.allowCalendar)
        assertTrue(json.encodeToString(msg.copy(allowCalendar = true)).contains("\"allow_calendar\":true"))
    }
}
