package com.example.hermes.features.workspace.forum

import com.example.hermes.core.model.ApprovalPayload
import com.example.hermes.core.model.ForumMessage
import com.example.hermes.core.model.ForumRoom
import com.example.hermes.core.model.HermesSseEvent
import com.example.hermes.core.model.OWNER_NAME
import com.example.hermes.core.model.ReplyState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A room's turn: who answers, what each agent is told, and what it answered. */
class ForumTurnsTest {
    private val room = ForumRoom(id = "r", name = "Conseil", topic = "Salon Vannes", members = listOf("gaston", "freya"))
    private val names = mapOf("gaston" to "Gaston", "freya" to "Freya")
    private fun name(id: String) = names[id] ?: id

    private fun owner(text: String) = ForumMessage(roomId = "r", sender = OWNER_NAME, fromOwner = true, content = text)
    private fun agent(id: String, text: String) = ForumMessage(roomId = "r", sender = id, content = text)

    @Test
    fun `a message without mention goes to every member, in room order`() {
        assertEquals(listOf("gaston", "freya"), targetsOf("Des idées ?", room.members, ::name))
    }

    @Test
    fun `mentions pick the members, by profile or display name`() {
        assertEquals(listOf("freya"), targetsOf("@Freya ton avis ?", room.members, ::name))
        assertEquals(listOf("gaston", "freya"), targetsOf("@freya puis @GASTON", room.members, ::name))
        assertEquals(listOf("gaston", "freya"), targetsOf("@Inconnu bonjour", room.members, ::name))
    }

    @Test
    fun `an agent is told what was said since its last turn, not its own words`() {
        val transcript = listOf(
            owner("Bonjour"),
            agent("gaston", "Salut"),
            agent("freya", "Hello"),
            owner("Un plan pour le salon ?"),
            agent("freya", "Stand B12"),
        )

        val prompt = promptFor(room, transcript, "gaston", ::name)

        assertTrue(prompt.contains("Conseil"))
        assertTrue(prompt.contains("Salon Vannes"))
        assertTrue(prompt.contains("Freya : Hello"))
        assertTrue(prompt.contains("$OWNER_NAME : Un plan pour le salon ?"))
        assertTrue(prompt.contains("Freya : Stand B12"))
        assertFalse(prompt.contains("Bonjour"))
        assertFalse(prompt.contains("Salut"))
        assertTrue(prompt.contains("Gaston"))
    }

    @Test
    fun `the reply is the streamed text`() = runBlocking {
        val reply = collectReply(flowOf(HermesSseEvent.Token("Stand "), HermesSseEvent.Token("B12"), HermesSseEvent.StreamEnd("s")))

        assertEquals(ReplyState.DONE, reply.state)
        assertEquals("Stand B12", reply.text)
    }

    @Test
    fun `an agent waiting for a decision is reported as such`() = runBlocking {
        val reply = collectReply(
            flowOf(HermesSseEvent.Token("Je lance "), HermesSseEvent.ApprovalRequested(ApprovalPayload()), HermesSseEvent.Token("jamais lu"))
        )

        assertEquals(ReplyState.WAITING, reply.state)
        assertEquals("Je lance ", reply.text)
    }

    @Test
    fun `an error or an empty stream is a failed reply`() = runBlocking {
        assertEquals(ReplyState.FAILED, collectReply(flowOf(HermesSseEvent.Error("quota"))).state)
        assertEquals("quota", collectReply(flowOf(HermesSseEvent.Error("quota"))).text)
        assertEquals(ReplyState.FAILED, collectReply(flowOf(HermesSseEvent.StreamEnd("s"))).state)
    }
}
