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
