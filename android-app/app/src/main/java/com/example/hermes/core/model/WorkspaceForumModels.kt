package com.example.hermes.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ParticipantType {
    USER,
    INTERNAL_AGENT,
    EXTERNAL_A2A
}

@Serializable
data class ForumParticipant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ParticipantType,
    val roleTitle: String = "",
    val avatarColorHex: String = "#58A6FF",
    val a2aEndpointUrl: String? = null,
    val a2aAuthToken: String? = null,
    val isOnline: Boolean = true
)

@Serializable
data class A2AExternalAgent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val endpointUrl: String,
    val authToken: String = "",
    val protocolVersion: String = "A2A/1.0",
    val capabilities: List<String> = emptyList(),
    val isReachable: Boolean = true,
    val lastPingAt: Long = System.currentTimeMillis()
)

@Serializable
data class ForumRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val topic: String = "",
    val iconEmoji: String = "🏛️",
    val participants: List<ForumParticipant> = emptyList(),
    val isA2AEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ForumMessage(
    val id: String = UUID.randomUUID().toString(),
    val roomId: String,
    val senderName: String,
    val senderType: ParticipantType,
    val content: String,
    val mentionedAgents: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isGenerating: Boolean = false,
    val a2aSourceEndpoint: String? = null
)
