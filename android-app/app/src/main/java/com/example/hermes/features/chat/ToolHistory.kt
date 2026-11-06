package com.example.hermes.features.chat

import com.example.hermes.core.model.ChatMessage
import com.example.hermes.core.model.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val historyJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * The server stores raw OpenAI-style history: tool names live in `function.name` and results are
 * separate `role: "tool"` messages. Fold them into the assistant tool calls so each card shows its
 * name and result, drop the tool messages, and never leave a past call in the "running" state.
 */
fun normalizeHistory(messages: List<ChatMessage>): List<ChatMessage> {
    val results = messages
        .filter { it.role == "tool" && !it.toolCallId.isNullOrBlank() }
        .associate { it.toolCallId!! to it.content }

    return messages
        .filter { it.role != "tool" }
        .map { it.takeIf { m -> m.role == "user" }?.let { m -> m.copy(content = stripAttachmentSuffix(m.content)) } ?: it }
        .map { msg ->
            val calls = msg.toolCalls ?: return@map msg
            msg.copy(toolCalls = calls.map { it.resolved(results) })
        }
}

private fun ToolCall.resolved(results: Map<String, String>): ToolCall = copy(
    name = name.ifBlank { function?.name.orEmpty() },
    args = args ?: function?.arguments?.let { parseArgs(it) },
    output = output ?: results[id] ?: ""
)

private fun parseArgs(raw: String): JsonElement? =
    runCatching { historyJson.parseToJsonElement(raw) }.getOrNull()
