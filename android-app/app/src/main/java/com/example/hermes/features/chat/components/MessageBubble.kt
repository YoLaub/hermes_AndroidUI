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
            .padding(vertical = 6.dp),
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
    // Strip XML tool calling syntax (<function_calls>...</function_calls>)
    if (raw.contains("function_calls", ignoreCase = true)) {
        raw = raw.replace(Regex("<(?:\\s*｜\\s*DSML\\s*[｜|]\\s*)?function_calls>[\\s\\S]*?</(?:\\s*｜\\s*DSML\\s*[｜|]\\s*)?function_calls>", RegexOption.IGNORE_CASE), "")
        raw = raw.replace(Regex("<(?:\\s*｜\\s*DSML\\s*[｜|]\\s*)?function_calls(?:>|$)[\\s\\S]*$", RegexOption.IGNORE_CASE), "")
        raw = raw.replace(Regex("<\\s*｜\\s*DSML\\s*[｜|]\\s*", RegexOption.IGNORE_CASE), "")
    }

    if (!explicitReasoning.isNullOrBlank()) {
        // Also strip any redundant <think> tags from content if reasoning was already provided
        val stripped = raw.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|channel>thought\\n[\\s\\S]*?<channel\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|turn\\|>thinking\\n[\\s\\S]*?<turn\\|>", RegexOption.IGNORE_CASE), "")
            .trim()
        return Pair(explicitReasoning, stripped)
    }

    // Check for inline think blocks
    val thinkRegex = Regex("<think>([\\s\\S]*?)</think>", RegexOption.IGNORE_CASE)
    val match = thinkRegex.find(raw)
    if (match != null) {
        val extracted = match.groupValues[1].trim()
        val remaining = raw.removeRange(match.range).trim()
        return Pair(extracted.ifBlank { null }, remaining)
    }

    // Gemma/other channel thought format
    val channelRegex = Regex("<\\|channel>thought\\n([\\s\\S]*?)<channel\\|>", RegexOption.IGNORE_CASE)
    val channelMatch = channelRegex.find(raw)
    if (channelMatch != null) {
        val extracted = channelMatch.groupValues[1].trim()
        val remaining = raw.removeRange(channelMatch.range).trim()
        return Pair(extracted.ifBlank { null }, remaining)
    }

    return Pair(null, raw.trim())
}
