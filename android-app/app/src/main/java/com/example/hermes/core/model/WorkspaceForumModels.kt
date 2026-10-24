package com.example.hermes.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A room where several Hermes profiles talk with the owner. The server has no room: each member answers in a real
 * session of its own profile ([sessions], created on its first turn), and the phone keeps the room's transcript.
 */
@Serializable
data class ForumRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val topic: String = "",
    val iconEmoji: String = "🏛️",
    /** Profile ids, in speaking order. */
    val members: List<String> = emptyList(),
    /** Profile id → that member's session for this room. */
    val sessions: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class ReplyState {
    DONE,
    /** The agent stopped for an approval or a question, to answer in its session. */
    WAITING,
    FAILED,
}

@Serializable
data class ForumMessage(
    val id: String = UUID.randomUUID().toString(),
    val roomId: String,
    /** [OWNER_NAME] for the owner, else the member's profile id. */
    val sender: String,
    val fromOwner: Boolean = false,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val state: ReplyState = ReplyState.DONE,
    /** The member's session the reply came from. */
    val sessionId: String? = null,
)

/** Everything the phone keeps for the forum. */
@Serializable
data class ForumData(
    val rooms: List<ForumRoom> = emptyList(),
    val messages: List<ForumMessage> = emptyList(),
)
