package com.example.hermes.core.mobilecontrol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundSyncTest {

    private fun session(id: String = "ses_1") = MobileControlSession(
        id = id, targetPackage = "com.linkedin.android", targetAppName = "LinkedIn", allowedProfile = "john",
        mode = MobileControlMode.INTERACTION, startedAt = 0L, durationSeconds = 900, expiresAt = 900_000L
    )

    private val none = SessionView(null, null)
    private val pending = SessionView(session(), null)
    private val active = SessionView(null, session())

    private class Calls {
        var starts = 0
        var stops = 0
    }

    private fun sync(views: MutableStateFlow<SessionView>, calls: Calls) =
        ForegroundSync(views, start = { calls.starts++ }, stop = { calls.stops++ }, stopDelayMs = 1_000)

    @Test
    fun noSessionAtStartupNeverStopsAServiceThatWasNeverStarted() = runTest {
        val calls = Calls()
        val job = launch { sync(MutableStateFlow(none), calls).run() }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, calls.starts)
        assertEquals(0, calls.stops)
        job.cancel()
    }

    @Test
    fun everyViewChangeWhileRunningRestartsTheServiceSoARefusedStartIsRetriedAndTheNotificationRefreshed() = runTest {
        val calls = Calls()
        val views = MutableStateFlow(pending)
        val job = launch { sync(views, calls).run() }
        runCurrent()
        assertEquals(1, calls.starts)
        views.value = active
        runCurrent()
        assertEquals(2, calls.starts)
        job.cancel()
    }

    @Test
    fun stopIsDelayedSoTheServiceHasTimeToCallStartForeground() = runTest {
        val calls = Calls()
        val views = MutableStateFlow(pending)
        val job = launch { sync(views, calls).run() }
        runCurrent()
        views.value = none            // refused or ended a few milliseconds after the start
        runCurrent()
        assertEquals("must not stop immediately", 0, calls.stops)
        advanceTimeBy(1_100); runCurrent()
        assertEquals(1, calls.stops)
        job.cancel()
    }

    @Test
    fun aNewSessionWithinTheDelayCancelsThePendingStop() = runTest {
        val calls = Calls()
        val views = MutableStateFlow(active)
        val job = launch { sync(views, calls).run() }
        runCurrent()
        views.value = none
        runCurrent()
        advanceTimeBy(400)
        views.value = pending
        runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, calls.stops)
        assertEquals(2, calls.starts)
        job.cancel()
    }
}
