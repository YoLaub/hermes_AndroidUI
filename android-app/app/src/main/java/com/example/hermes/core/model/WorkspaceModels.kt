package com.example.hermes.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** The phone's owner in workspace data (notes, events, forum). Agents come from the profile list. */
const val OWNER_NAME = "Moi"

/** The name the demo data used for the owner before personas came from profiles; still read as the owner. */
private const val LEGACY_OWNER_NAME = "Yoann"

fun isOwnerName(name: String): Boolean = name == OWNER_NAME || name.equals(LEGACY_OWNER_NAME, ignoreCase = true)

/** [name], with the legacy owner name replaced by [OWNER_NAME]. */
fun ownerRenamed(name: String): String = if (isOwnerName(name)) OWNER_NAME else name

private val legacyOwnerWord = Regex("""\b$LEGACY_OWNER_NAME\b""", RegexOption.IGNORE_CASE)

fun ForumRoom.withOwnerRenamed(): ForumRoom = copy(
    topic = topic.replace(legacyOwnerWord, OWNER_NAME),
    participants = participants.map { it.copy(name = ownerRenamed(it.name)) },
)

fun ForumMessage.withOwnerRenamed(): ForumMessage = copy(senderName = ownerRenamed(senderName))

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
