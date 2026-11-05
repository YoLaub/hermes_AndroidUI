package com.example.hermes.features.chat

import com.example.hermes.core.model.ChatAttachment

/** A message typed while a reply is still streaming; sent once the current turn ends (FIFO, like the WebUI). */
data class QueuedMessage(val text: String, val attachments: List<ChatAttachment>)

enum class SendPlan { Send, Queue }

fun sendPlan(isStreaming: Boolean): SendPlan = if (isStreaming) SendPlan.Queue else SendPlan.Send

fun List<QueuedMessage>.dequeue(): Pair<QueuedMessage, List<QueuedMessage>>? =
    firstOrNull()?.let { it to drop(1) }

fun List<QueuedMessage>.withoutIndex(index: Int): List<QueuedMessage> =
    if (index in indices) filterIndexed { i, _ -> i != index } else this
