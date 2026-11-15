package com.example.hermes.core.mobilecontrol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DraftHandoffTest {
    @Test fun controlEndsBeforeTheExternalComposerOpens() = runBlocking {
        val events = mutableListOf<String>()
        handoffDraft(prepare = { events.add("download"); "image" }, authorized = { true },
            endControl = { events.add("stop") }, open = { events.add("open") }, discard = { events.add("discard") })
        assertEquals(listOf("download", "stop", "open"), events)
    }
    @Test fun stoppingDuringDownloadDiscardsTheImageAndNeverOpensLinkedin() = runBlocking {
        val events = mutableListOf<String>()
        try {
            handoffDraft(prepare = { "image" }, authorized = { false }, endControl = { events.add("stop") },
                open = { events.add("open") }, discard = { events.add("discard") })
            fail("expired session accepted")
        } catch (_: DraftSessionExpired) {}
        assertEquals(listOf("discard"), events)
    }
    @Test fun aFailedDownloadDoesNotEndControlAndAFailedShareNeverRestartsIt() = runBlocking {
        val events = mutableListOf<String>()
        try {
            handoffDraft<String>(prepare = { error("download failed") }, authorized = { true },
                endControl = { events.add("stop") }, open = { events.add("open") }, discard = { events.add("discard") })
        } catch (_: IllegalStateException) {}
        assertTrue(events.isEmpty())
        try {
            handoffDraft(prepare = { "image" }, authorized = { true }, endControl = { events.add("stop") },
                open = { error("LinkedIn missing") }, discard = { events.add("discard") })
        } catch (_: IllegalStateException) {}
        assertEquals(listOf("stop", "discard"), events)
    }
}
