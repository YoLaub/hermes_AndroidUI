package com.example.hermes.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Workspace data saved before the owner became "Moi" is shown with the new name. */
class OwnerRenameTest {
    @Test
    fun `a forum room names the owner Moi`() {
        val room = ForumRoom(
            name = "Conseil",
            topic = "Tour de table avec Yoann, Mario et Gaston",
            participants = listOf(
                ForumParticipant(name = "Yoann", type = ParticipantType.USER),
                ForumParticipant(name = "Mario", type = ParticipantType.INTERNAL_AGENT),
            ),
        ).withOwnerRenamed()
        assertEquals(listOf(OWNER_NAME, "Mario"), room.participants.map { it.name })
        assertEquals("Tour de table avec $OWNER_NAME, Mario et Gaston", room.topic)
    }

    @Test
    fun `a forum message from the owner is signed Moi`() {
        val message = ForumMessage(roomId = "r", senderName = "yoann", senderType = ParticipantType.USER, content = "Bonjour")
        assertEquals(OWNER_NAME, message.withOwnerRenamed().senderName)
    }
}
