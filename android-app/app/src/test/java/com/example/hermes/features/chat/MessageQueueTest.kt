package com.example.hermes.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageQueueTest {
    private fun q(text: String) = QueuedMessage(text, emptyList())

    @Test
    fun `a send while a reply is streaming is queued, otherwise sent`() {
        assertEquals(SendPlan.Queue, sendPlan(isStreaming = true))
        assertEquals(SendPlan.Send, sendPlan(isStreaming = false))
    }

    @Test
    fun `queued messages are drained first in first out`() {
        val queue = listOf(q("a"), q("b"), q("c"))
        val (first, rest) = queue.dequeue()!!
        assertEquals("a", first.text)
        assertEquals(listOf("b", "c"), rest.map { it.text })
    }

    @Test
    fun `draining an empty queue gives nothing`() {
        assertNull(emptyList<QueuedMessage>().dequeue())
    }

    @Test
    fun `removing a queued message keeps the order of the others`() {
        val queue = listOf(q("a"), q("b"), q("c"))
        assertEquals(listOf("a", "c"), queue.withoutIndex(1).map { it.text })
        assertEquals(queue, queue.withoutIndex(9))
    }
}
