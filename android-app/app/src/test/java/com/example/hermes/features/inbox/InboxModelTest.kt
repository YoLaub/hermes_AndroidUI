package com.example.hermes.features.inbox

import com.example.hermes.core.model.ApprovalPayload
import com.example.hermes.core.model.MemoryAction
import com.example.hermes.core.model.MemoryValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class InboxModelTest {
    private val approval = ApprovalPayload(id = "ap1", tool = "terminal", command = "composer create-project")
    private val actions = listOf(MemoryAction(id = "a7f3c2-1", label = "codeigniter.com"), MemoryAction(id = "b1-2"))
    private val memory = listOf(
        MemoryValidation(eventId = "e1", preview = "x"),
        MemoryValidation(eventId = "e2"),
        MemoryValidation(eventId = "e3"),
    )

    @Test
    fun `counts add up the three sources`() {
        val counts = inboxCounts(approval, actions, memory)
        assertEquals(InboxCounts(tools = 1, actions = 2, memory = 3), counts)
        assertEquals(6, counts.total)
        assertEquals(0, inboxCounts(null, emptyList(), emptyList()).total)
    }

    @Test
    fun `all lists tools first, then actions, then memory`() {
        val items = inboxItems(approval, actions, memory, InboxFilter.All)
        assertEquals(6, items.size)
        assertTrue(items[0] is InboxItem.Tool)
        assertTrue(items[1] is InboxItem.Action)
        assertTrue(items[5] is InboxItem.Memory)
    }

    @Test
    fun `a filter keeps one source`() {
        assertEquals(1, inboxItems(approval, actions, memory, InboxFilter.Tools).size)
        assertEquals(2, inboxItems(approval, actions, memory, InboxFilter.Actions).size)
        assertTrue(inboxItems(approval, actions, memory, InboxFilter.Memory).all { it is InboxItem.Memory })
        assertTrue(inboxItems(null, actions, memory, InboxFilter.Tools).isEmpty())
    }

    @Test
    fun `token errors offer to change the token`() {
        assertTrue(isOwnerTokenError("Jeton propriétaire refusé ou absent"))
        assertTrue(isOwnerTokenError("Saisis d'abord le jeton propriétaire"))
        assertFalse(isOwnerTokenError("Service mémoire injoignable"))
        assertFalse(isOwnerTokenError(null))
    }

    private val zone = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-10-19T12:40:00Z")

    @Test
    fun `times of today read as hours, older ones as dates`() {
        assertEquals("14:32", shortTime("2026-10-19T12:32:00Z", now, zone))
        assertEquals("hier", shortTime("2026-10-18T20:00:00Z", now, zone))
        assertEquals("12/10", shortTime("2026-10-12T08:00:00Z", now, zone))
    }

    @Test
    fun `an unreadable time is kept as sent and a missing one is empty`() {
        assertEquals("bientôt", shortTime("bientôt", now, zone))
        assertEquals("", shortTime(null, now, zone))
    }
}
