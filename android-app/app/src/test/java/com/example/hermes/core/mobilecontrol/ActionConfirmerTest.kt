package com.example.hermes.core.mobilecontrol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionConfirmerTest {

    private fun request(id: String = "c1") =
        ConfirmationRequest(id, "sms_send", "Alice", "+33612345678", "Bonjour", emptySet())

    private class Spy {
        val shown = mutableListOf<String>()
        val dismissed = mutableListOf<String>()
    }

    private fun kotlinx.coroutines.test.TestScope.confirmer(spy: Spy) =
        ActionConfirmer(show = { spy.shown += it.id }, dismiss = { spy.dismissed += it }, now = { currentTime })

    @Test
    fun theUsersAcceptShowsOnceAndResolvesAccepted() = runTest {
        val spy = Spy()
        val c = confirmer(spy)
        val result = async { c.confirm(request()) }
        runCurrent()
        assertEquals(listOf("c1"), spy.shown)
        c.onUserDecision("c1", accept = true)
        assertEquals(Decision.ACCEPTED, result.await())
        assertEquals(listOf("c1"), spy.dismissed)
    }

    @Test
    fun theUsersRefusalResolvesRefused() = runTest {
        val c = confirmer(Spy())
        val result = async { c.confirm(request()) }
        runCurrent()
        c.onUserDecision("c1", accept = false)
        assertEquals(Decision.REFUSED, result.await())
    }

    @Test
    fun noAnswerWithinSixtySecondsIsExpiredAndTheNotificationIsDismissed() = runTest {
        val spy = Spy()
        val c = confirmer(spy)
        val result = async { c.confirm(request()) }
        runCurrent()
        advanceTimeBy(59_000)
        runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(Decision.EXPIRED, result.await())
        assertEquals(listOf("c1"), spy.dismissed)
    }

    @Test
    fun aTapAfterTheDelayCannotAcceptAnything() = runTest {
        val c = confirmer(Spy())
        val result = async { c.confirm(request()) }
        runCurrent()
        advanceTimeBy(61_000)
        runCurrent()
        c.onUserDecision("c1", accept = true)      // late tap on a stale notification
        assertEquals(Decision.EXPIRED, result.await())
    }

    @Test
    fun aSecondRequestWhileOneIsPendingIsBusyAndShowsNothing() = runTest {
        val spy = Spy()
        val c = confirmer(spy)
        val first = async { c.confirm(request("c1")) }
        runCurrent()
        assertNull(c.confirm(request("c2")))
        assertEquals(listOf("c1"), spy.shown)
        c.onUserDecision("c1", true)
        assertEquals(Decision.ACCEPTED, first.await())
    }

    @Test
    fun aReplayedOrUnknownDecisionDoesNothing() = runTest {
        val c = confirmer(Spy())
        val result = async { c.confirm(request()) }
        runCurrent()
        c.onUserDecision("other", true)
        assertFalse(result.isCompleted)
        c.onUserDecision("c1", false)
        assertEquals(Decision.REFUSED, result.await())
        c.onUserDecision("c1", true)               // replay
    }

    @Test
    fun cancelAllRefusesWhatIsPending() = runTest {
        val spy = Spy()
        val c = confirmer(spy)
        val result = async { c.confirm(request()) }
        runCurrent()
        c.cancelAll()
        assertEquals(Decision.REFUSED, result.await())
        assertEquals(listOf("c1"), spy.dismissed)
    }

    @Test
    fun theNextRequestIsAcceptedOnceTheFirstIsResolved() = runTest {
        val c = confirmer(Spy())
        val first = async { c.confirm(request("c1")) }
        runCurrent()
        c.onUserDecision("c1", false)
        first.await()
        val second = async { c.confirm(request("c2")) }
        runCurrent()
        c.onUserDecision("c2", true)
        assertEquals(Decision.ACCEPTED, second.await())
    }
}
