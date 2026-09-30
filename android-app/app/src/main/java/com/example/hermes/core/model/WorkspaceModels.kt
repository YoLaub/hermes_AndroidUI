package com.example.hermes.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AgentNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val author: String = "Yoann",
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
