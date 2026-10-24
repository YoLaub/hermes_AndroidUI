package com.example.hermes.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** The phone's owner in workspace data (notes, events, forum). Agents come from the profile list. */
const val OWNER_NAME = "Moi"

/** The name the demo data used for the owner before personas came from profiles; still read as the owner. */
private const val LEGACY_OWNER_NAME = "Yoann"

fun isOwnerName(name: String): Boolean = name == OWNER_NAME || name.equals(LEGACY_OWNER_NAME, ignoreCase = true)

/** Whether [name] is the person a filter on [filter] selects; a filter on the owner also matches older data. */
fun matchesPerson(name: String, filter: String): Boolean =
    if (filter == OWNER_NAME) isOwnerName(name) else name.equals(filter, ignoreCase = true)

@Serializable
data class AgentNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val author: String = OWNER_NAME,
    val authorRole: String = "user", // "user" or "agent"
    val tags: List<String> = emptyList(),
    val audioFilePath: String? = null,
    val audioDurationSec: Int? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TranscriptionState(
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val partialText: String = "",
    val finalText: String = "",
    val isOffline: Boolean = false,
    val rmsDb: Float = 0f,
    val durationSec: Int = 0,
    val audioFilePath: String? = null,
    val errorMessage: String? = null
)
