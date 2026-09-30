package com.example.hermes.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.theme.*

import androidx.compose.runtime.remember

sealed interface MarkdownBlock {
    data class Code(val lang: String, val code: String) : MarkdownBlock
    data class Content(val annotatedText: AnnotatedString) : MarkdownBlock
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val clipboardManager = LocalClipboardManager.current

    // Cache the parsed blocks so we don't re-parse markdown on every recomposition
    val blocks = remember(text, color) {
        val parts = text.split("```")
        parts.mapIndexed { index, part ->
            if (index % 2 == 1) {
                val lines = part.lines()
                val lang = lines.firstOrNull()?.trim() ?: ""
                val code = if (lines.size > 1) lines.drop(1).joinToString("\n") else part
                MarkdownBlock.Code(lang = lang.ifEmpty { "code" }, code = code.trimEnd())
            } else {
                MarkdownBlock.Content(annotatedText = parseInlineMarkdown(part, color))
            }
        }
    }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HermesCodeBackground)
                            .border(1.dp, OnyxBorder, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.lang,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HermesTextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(block.code)) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = HermesTextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    text = block.code,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp
                                    ),
                                    color = HermesPrimary
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Content -> {
                    if (block.annotatedText.isNotEmpty()) {
                        SelectionContainer {
                            Text(
                                text = block.annotatedText,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = color,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String, defaultColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            when {
                // Bold: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline code: `code`
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = HermesCodeBackground,
                                color = HermesSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(" ${text.substring(i + 1, end)} ")
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: *text* (single star, not double)
                text[i] == '*' && (i + 1 < len && text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1 && (end + 1 >= len || text[end + 1] != '*')) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    // Fast slice scan: find the next delimiter (* or `) and append the slice all at once
                    val nextSpecial = text.indexOfAny(charArrayOf('*', '`'), i)
                    if (nextSpecial == -1) {
                        append(text.substring(i))
                        break
                    } else {
                        append(text.substring(i, nextSpecial))
                        i = nextSpecial
                    }
                }
            }
        }
    }
}
