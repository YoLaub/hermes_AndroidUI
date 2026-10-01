package com.example.hermes.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.hermes.core.model.ChatMessage
import com.example.hermes.theme.*

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Agent Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Agent",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Main Bubble Card
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) HermesUserBubble else HermesAssistantBubble
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isUser) HermesPrimary.copy(alpha = 0.4f) else OnyxBorder
                    )
                ),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Attachments if any
                    message.attachments?.takeIf { it.isNotEmpty() }?.let { attachments ->
                        attachments.forEach { att ->
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OnyxDarkSurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = HermesPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = att.filename,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HermesTextPrimary
                                )
                            }
                        }
                    }

                    // Extract any inline <think>...</think> from content if reasoning is not set
                    val (extractedReasoning, displayContent) = parseContentAndReasoning(
                        content = message.content,
                        explicitReasoning = message.reasoning
                    )

                    // Reasoning Trace if present (assistant)
                    extractedReasoning?.takeIf { it.isNotBlank() }?.let { reasoning ->
                        ReasoningCard(reasoning = reasoning, isStreaming = false)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Tool Calls if present (assistant)
                    message.toolCalls?.takeIf { it.isNotEmpty() }?.let { toolCalls ->
                        toolCalls.forEach { toolCall ->
                            ToolCallCard(toolCall = toolCall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Cleaned Message Content
                    if (displayContent.isNotBlank()) {
                        MarkdownText(
                            text = displayContent,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // User Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(OnyxDarkSurfaceVariant)
                    .border(1.dp, OnyxBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = HermesTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun parseContentAndReasoning(
    content: String,
    explicitReasoning: String?
): Pair<String?, String> {
    var raw = content
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .trim()

    // Safely strip XML tool calling syntax (<function_calls>...</function_calls>)
    raw = stripXmlBlock(raw, "function_calls")

    if (!explicitReasoning.isNullOrBlank()) {
        val stripped = stripAllThoughtTags(raw).trim()
        return Pair(explicitReasoning.trim(), stripped)
    }

    // Check for standard <think>...</think> tags (both complete and streaming in-progress)
    val thinkOpen = raw.indexOf("<think>", ignoreCase = true)
    if (thinkOpen != -1) {
        val thinkClose = raw.indexOf("</think>", thinkOpen + 7, ignoreCase = true)
        if (thinkClose != -1) {
            val extracted = raw.substring(thinkOpen + 7, thinkClose).trim()
            val remaining = (raw.substring(0, thinkOpen) + raw.substring(thinkClose + 8)).trim()
            return Pair(extracted.ifBlank { null }, remaining)
        } else {
            // In-progress think block (streaming)
            val extracted = raw.substring(thinkOpen + 7).trim()
            val remaining = raw.substring(0, thinkOpen).trim()
            return Pair(extracted.ifBlank { null }, remaining)
        }
    }

    // Check for Gemma/other channel thought format (<|channel>thought\n ... <channel|>)
    val channelOpenTag = "<|channel>thought"
    val channelCloseTag = "<channel|>"
    val channelOpen = raw.indexOf(channelOpenTag, ignoreCase = true)
    if (channelOpen != -1) {
        val contentStart = raw.indexOf('\n', channelOpen + channelOpenTag.length).let {
            if (it != -1 && it < channelOpen + channelOpenTag.length + 4) it + 1 else channelOpen + channelOpenTag.length
        }
        val channelClose = raw.indexOf(channelCloseTag, contentStart, ignoreCase = true)
        if (channelClose != -1) {
            val extracted = raw.substring(contentStart, channelClose).trim()
            val remaining = (raw.substring(0, channelOpen) + raw.substring(channelClose + channelCloseTag.length)).trim()
            return Pair(extracted.ifBlank { null }, remaining)
        } else {
            val extracted = raw.substring(contentStart).trim()
            val remaining = raw.substring(0, channelOpen).trim()
            return Pair(extracted.ifBlank { null }, remaining)
        }
    }

    // Check for turn thinking format (<|turn|>thinking\n ... <turn|>)
    val turnOpenTag = "<|turn|>thinking"
    val turnCloseTag = "<turn|>"
    val turnOpen = raw.indexOf(turnOpenTag, ignoreCase = true)
    if (turnOpen != -1) {
        val contentStart = raw.indexOf('\n', turnOpen + turnOpenTag.length).let {
            if (it != -1 && it < turnOpen + turnOpenTag.length + 4) it + 1 else turnOpen + turnOpenTag.length
        }
        val turnClose = raw.indexOf(turnCloseTag, contentStart, ignoreCase = true)
        if (turnClose != -1) {
            val extracted = raw.substring(contentStart, turnClose).trim()
            val remaining = (raw.substring(0, turnOpen) + raw.substring(turnClose + turnCloseTag.length)).trim()
            return Pair(extracted.ifBlank { null }, remaining)
        } else {
            val extracted = raw.substring(contentStart).trim()
            val remaining = raw.substring(0, turnOpen).trim()
            return Pair(extracted.ifBlank { null }, remaining)
        }
    }

    return Pair(null, raw.trim())
}

private fun stripXmlBlock(text: String, tagBaseName: String): String {
    var result = text
    while (true) {
        val openIdx = result.indexOf(tagBaseName, ignoreCase = true)
        if (openIdx == -1) break
        val tagStart = result.lastIndexOf('<', openIdx)
        if (tagStart == -1) break
        val tagOpenEnd = result.indexOf('>', openIdx)
        if (tagOpenEnd == -1) break

        val closeTag = "</$tagBaseName>"
        val closeIdx = result.indexOf(closeTag, tagOpenEnd + 1, ignoreCase = true)
        if (closeIdx != -1) {
            result = result.substring(0, tagStart) + result.substring(closeIdx + closeTag.length)
        } else {
            // Streaming / unclosed
            result = result.substring(0, tagStart)
            break
        }
    }
    return result
}

private fun stripAllThoughtTags(text: String): String {
    var s = text
    // Strip <think>...</think>
    while (true) {
        val o = s.indexOf("<think>", ignoreCase = true)
        if (o == -1) break
        val c = s.indexOf("</think>", o + 7, ignoreCase = true)
        if (c != -1) {
            s = s.substring(0, o) + s.substring(c + 8)
        } else {
            s = s.substring(0, o)
            break
        }
    }
    // Strip <|channel>thought...<channel|>
    while (true) {
        val o = s.indexOf("<|channel>thought", ignoreCase = true)
        if (o == -1) break
        val c = s.indexOf("<channel|>", o + 17, ignoreCase = true)
        if (c != -1) {
            s = s.substring(0, o) + s.substring(c + 10)
        } else {
            s = s.substring(0, o)
            break
        }
    }
    // Strip <|turn|>thinking...<turn|>
    while (true) {
        val o = s.indexOf("<|turn|>thinking", ignoreCase = true)
        if (o == -1) break
        val c = s.indexOf("<turn|>", o + 16, ignoreCase = true)
        if (c != -1) {
            s = s.substring(0, o) + s.substring(c + 7)
        } else {
            s = s.substring(0, o)
            break
        }
    }
    return s
}
