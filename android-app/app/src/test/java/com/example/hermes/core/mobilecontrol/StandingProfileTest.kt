package com.example.hermes.core.mobilecontrol

import com.example.hermes.core.accessibility.TapGuard
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** The phone side of a standing session: a phone dedicated to the agent trusts the relay's session. */
class StandingProfileTest {
    private val now = 1_000_000L
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val allConsents = listOf("screenshots", "calendar", "sms_read", "call_log_read", "sms_send", "call_place", "calendar_write")
    private val standingState = MobileSessionState(
        active = true, sessionId = "standing_1", profile = "phone-agent", targetPackage = "*",
        mode = "interaction", allow = allConsents, expiresInSeconds = 31_000_000
    )

    // ── Policy ────────────────────────────────────────────────────────────────

    @Test
    fun theDefaultPolicyIsTheStrictOne() {
        assertFalse(ControlPolicy.FULL.trustRelaySession)
        assertTrue(ControlPolicy.FULL.requireConfirmation)
    }

    @Test
    fun theLitePolicyTrustsTheRelayAndAsksNoConfirmation() {
        assertTrue(ControlPolicy.LITE.trustRelaySession)
        assertFalse(ControlPolicy.LITE.requireConfirmation)
    }

    // ── Adopting the relay's session ─────────────────────────────────────────

    @Test
    fun theStrictPhoneAdoptsARelaySessionWithoutAnyConsent() {
        val t = SessionConfirmation.onServerState(SessionView(null, null), standingState, now)
        assertEquals(emptySet<String>(), t.view.active!!.consents)
    }

    @Test
    fun theLitePhoneAdoptsTheRelaySessionWithItsConsentsAndNoAppLimit() {
        val t = SessionConfirmation.onServerState(SessionView(null, null), standingState, now, trustRelayConsents = true)
        val active = t.view.active!!
        assertEquals("phone-agent", active.allowedProfile)
        assertEquals(ANY_APP, active.targetPackage)
        assertEquals(allConsents.toSet(), active.consents)
    }

    // ── Package-scoped checks with "any app" ─────────────────────────────────

    @Test
    fun anyAppNeverRequiresASpecificForegroundApp() {
        assertFalse(CommandSessionGuard.needsTargetInForeground("click_element", ANY_APP))
        assertTrue(CommandSessionGuard.needsTargetInForeground("click_element", "com.linkedin.android"))
        assertFalse(CommandSessionGuard.needsTargetInForeground("launch_app", "com.linkedin.android"))
    }

    @Test
    fun aTapForAnyAppNeedsAForegroundAppButNotAParticularOne() {
        assertTrue(TapGuard.targetStillInForeground("com.android.settings", ANY_APP))
        assertFalse(TapGuard.targetStillInForeground(null, ANY_APP))
        assertFalse(TapGuard.targetStillInForeground("", ANY_APP))
        assertFalse(TapGuard.targetStillInForeground("com.other", "com.linkedin.android"))
    }

    // ── Launching ────────────────────────────────────────────────────────────

    @Test
    fun anyAppSessionLaunchesTheRequestedPackage() {
        assertEquals(LaunchTarget.Package("com.android.settings"), launchTarget(ANY_APP, "com.android.settings"))
    }

    @Test
    fun anyAppSessionWithoutAPackageHasNothingToLaunch() {
        val r = launchTarget(ANY_APP, null)
        assertEquals("INVALID_ARGUMENTS", (r as LaunchTarget.Rejected).code)
    }

    @Test
    fun aSessionBoundToAnAppKeepsLaunchingOnlyThatApp() {
        assertEquals(LaunchTarget.Package("com.linkedin.android"), launchTarget("com.linkedin.android", null))
        assertEquals(LaunchTarget.Package("com.linkedin.android"), launchTarget("com.linkedin.android", "com.linkedin.android"))
        val r = launchTarget("com.linkedin.android", "com.android.settings")
        assertEquals("APP_NOT_ALLOWED", (r as LaunchTarget.Rejected).code)
    }

    // ── Wire format ──────────────────────────────────────────────────────────

    @Test
    fun theLaunchPackageArrivesInTheCommandArguments() {
        val args = json.decodeFromString<MobileCommandArguments>("""{"package":"com.android.settings"}""")
        assertEquals("com.android.settings", args.packageName)
    }

    // ── Pairing as an agent phone ────────────────────────────────────────────

    private class Capture : PairingTransport {
        var body: String? = null
        override suspend fun postJson(url: String, body: String, headers: Map<String, String>): HttpReply {
            this.body = body
            return HttpReply(200, """{"ok":true,"device_token":"tok_x"}""")
        }
    }

    @Test
    fun pairingAsAnAgentPhoneSendsItsDeviceKind() {
        val t = Capture()
        runBlocking { PairingClient(t).pair("https://relay.example", "ABC123", "dev_1", "Agent", deviceKind = "agent_phone") }
        assertEquals("agent_phone", json.parseToJsonElement(t.body!!).jsonObject["device_kind"]!!.jsonPrimitive.content)
    }

    @Test
    fun anOrdinaryPairingStillSendsNoDeviceKind() {
        val t = Capture()
        runBlocking { PairingClient(t).pair("https://relay.example", "ABC123", "dev_1", "Phone") }
        assertNull(json.parseToJsonElement(t.body!!).jsonObject["device_kind"])
    }
}
