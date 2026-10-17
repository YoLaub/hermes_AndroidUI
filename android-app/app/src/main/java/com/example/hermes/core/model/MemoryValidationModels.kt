package com.example.hermes.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An item of the agent-memory service waiting for the owner's decision. [preview] is plain text, never markup. */
@Serializable
data class MemoryValidation(
    @SerialName("event_id") val eventId: String,
    @SerialName("occurred_at") val occurredAt: String? = null,
    val zone: String? = null,
    val origin: String? = null,
    val status: String? = null,
    val source: String? = null,
    val preview: String = ""
)

@Serializable
data class MemoryValidationsResponse(val items: List<MemoryValidation> = emptyList())

@Serializable
data class MemoryValidationDecisionRequest(val decision: String)

@Serializable
data class MemoryValidationDecisionResponse(val ok: Boolean = false)

/**
 * A request the action gate holds for the owner. [label] is the target as the agent asked for it (a command, an
 * address, a tool name): untrusted text, to be shown as plain text only. [id] is the one `admin actions` lists.
 */
@Serializable
data class MemoryAction(
    val id: String,
    val kind: String? = null,
    val profile: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val label: String = "",
    val reason: String? = null,
    @SerialName("requested_at") val requestedAt: String? = null
)

@Serializable
data class MemoryActionsResponse(val items: List<MemoryAction> = emptyList())
