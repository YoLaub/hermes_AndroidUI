package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class ConfirmationGateTest {

    private val t0 = 1_000_000L
    private fun request(id: String = "c1", kind: String = "sms_send", text: String? = "Bonjour") =
        ConfirmationRequest(id, kind, "Alice", "+33612345678", text, readKinds = emptySet())

    @Test
    fun aRequestOpensAndStaysPendingUntilTheUserDecides() {
        val gate = ConfirmationGate()
        assertEquals(ConfirmationGate.Open.OPENED, gate.open(request(), t0))
        assertEquals("c1", gate.pending?.id)
    }

    @Test
    fun theUserAcceptingWithinTheDelayIsTheOnlyWayToAccepted() {
        val gate = ConfirmationGate()
        gate.open(request(), t0)
        assertEquals(Decision.ACCEPTED, gate.decide("c1", accept = true, nowMs = t0 + 59_999))
        assertNull(gate.pending)
    }

    @Test
    fun refusingIsRefused() {
        val gate = ConfirmationGate()
        gate.open(request(), t0)
        assertEquals(Decision.REFUSED, gate.decide("c1", accept = false, nowMs = t0 + 1))
    }

    @Test
    fun acceptingAfterTheDelayIsNeverAccepted() {
        val gate = ConfirmationGate()
        gate.open(request(), t0)
        assertEquals(Decision.EXPIRED, gate.decide("c1", accept = true, nowMs = t0 + 60_000))
        assertNull(gate.pending)
    }

    @Test
    fun aDecisionIsUsedOnce() {
        val gate = ConfirmationGate()
        gate.open(request(), t0)
        assertEquals(Decision.ACCEPTED, gate.decide("c1", true, t0 + 1))
        assertNull(gate.decide("c1", true, t0 + 2))   // a replayed tap does nothing
    }

    @Test
    fun anUnknownOrStaleIdChangesNothing() {
        val gate = ConfirmationGate()
        gate.open(request(), t0)
        assertNull(gate.decide("other", true, t0 + 1))
        assertEquals("c1", gate.pending?.id)
    }

    @Test
    fun onlyOneConfirmationAtATime() {
        val gate = ConfirmationGate()
        gate.open(request("c1"), t0)
        assertEquals(ConfirmationGate.Open.BUSY, gate.open(request("c2"), t0 + 1))
        assertEquals("c1", gate.pending?.id)
    }

    @Test
    fun anExpiredPendingIsReplacedByANewRequest() {
        val gate = ConfirmationGate()
        gate.open(request("c1"), t0)
        assertEquals(ConfirmationGate.Open.OPENED, gate.open(request("c2"), t0 + 60_000))
        assertEquals("c2", gate.pending?.id)
    }

    @Test
    fun expireResolvesThePendingOnlyOncePastTheDeadline() {
        val gate = ConfirmationGate()
        gate.open(request(), t0)
        assertNull(gate.expire(t0 + 59_999))
        assertEquals("c1", gate.expire(t0 + 60_000))
        assertNull(gate.expire(t0 + 70_000))
        assertNull(gate.pending)
    }

    @Test
    fun theRequestShownIsTheRequestThatIsSentBecauseItCannotChange() {
        val r = request(text = "Bonjour")
        val gate = ConfirmationGate()
        gate.open(r, t0)
        assertSame(r, gate.pending)
    }

    @Test
    fun cancelDropsThePendingWithoutAcceptingIt() {
        val gate = ConfirmationGate()
        gate.open(request(), t0)
        gate.cancel()
        assertNull(gate.pending)
        assertNull(gate.decide("c1", true, t0 + 1))
    }

    // ── The banner: what external content was read before this proposal ──

    @Test
    fun noBannerWhenNothingExternalWasRead() {
        assertNull(ConfirmationText.banner(emptySet()))
    }

    @Test
    fun theBannerListsWhatWasReadInAStableOrder() {
        val banner = ConfirmationText.banner(setOf(Consent.SMS_READ, "screen", Consent.CALENDAR))!!
        assertEquals("Proposé après lecture de : écran, calendrier, SMS", banner)
    }
}
