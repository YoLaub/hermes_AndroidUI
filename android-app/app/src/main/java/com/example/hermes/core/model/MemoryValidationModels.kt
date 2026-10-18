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

/** A profile of the memory service with the zones it may read and write. Never carries a token. */
@Serializable
data class MemoryProfile(
    val name: String,
    val state: String = "active",
    val read: List<String> = emptyList(),
    val write: List<String> = emptyList(),
    /** "high" or "normal"; read-only here, only the administrator's command can change it. */
    @SerialName("gate_level") val gateLevel: String? = null
)

@Serializable
data class MemoryProfilesResponse(val items: List<MemoryProfile> = emptyList())

@Serializable
data class MemoryProfileCreateRequest(val name: String, val read: List<String>, val write: List<String>)

@Serializable
data class MemoryProfileGrantsRequest(val read: List<String>, val write: List<String>)

/** The answer to a creation: [token] is shown once and then dropped; it is masked if the object is ever printed. */
@Serializable
data class MemoryProfileCreated(val name: String, val token: String) {
    override fun toString(): String = "MemoryProfileCreated(name=$name, token=***)"
}

/**
 * What the owner entrusted to one profile for one task, until [expiresAt]. [label] is the owner's own text. [state]
 * is "active", "expired" or "revoked", as the server judged it.
 */
@Serializable
data class MemoryMandate(
    val id: String,
    val profile: String = "",
    val label: String = "",
    val hosts: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val state: String = "active"
)

@Serializable
data class MemoryMandatesResponse(val items: List<MemoryMandate> = emptyList())

@Serializable
data class MemoryMandateCreateRequest(
    val profile: String,
    val label: String,
    val hosts: List<String>,
    val folders: List<String>,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class MemoryMandateCreated(val id: String)
