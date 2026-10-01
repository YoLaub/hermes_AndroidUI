package com.example.hermes.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.hermes.core.model.ChatAttachment
import com.example.hermes.core.model.CommandInfo
import com.example.hermes.theme.*

@Composable
fun ChatInputField(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelStream: () -> Unit,
    isStreaming: Boolean,
    pendingAttachments: List<ChatAttachment> = emptyList(),
    availableCommands: List<CommandInfo> = emptyList(),
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    onAttachClicked: () -> Unit = {},
    onCommandSelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val matchingCommands = remember(inputText, availableCommands) {
        if (inputText.startsWith("/")) {
            val query = inputText.removePrefix("/").lowercase().trim()
            availableCommands.filter { cmd ->
                val name = cmd.name.removePrefix("/").lowercase()
                query.isEmpty() || name.contains(query) || cmd.description.lowercase().contains(query)
            }
        } else {
            emptyList()
        }
    }

    Surface(
        color = OnyxDarkSurface,
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 12.dp)
        ) {
            // Slash Command Autocomplete Popup
            if (matchingCommands.isNotEmpty() && !isStreaming) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OnyxDarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OnyxBorder),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "COMMANDES DISPONIBLES",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = HermesTextMuted,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(matchingCommands, key = { it.name }) { cmd ->
                                val isRestart = cmd.name.equals("/restart", ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (onCommandSelected != null) {
                                                onCommandSelected(cmd.name)
                                            } else {
                                                onInputTextChanged(cmd.name)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isRestart) HermesWarningContainer.copy(alpha = 0.5f)
                                                else HermesPrimaryContainer.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isRestart -> Icons.Default.RestartAlt
                                                cmd.name.startsWith("/help") -> Icons.Default.HelpOutline
                                                else -> Icons.Default.Terminal
                                            },
                                            contentDescription = null,
                                            tint = if (isRestart) HermesWarning else HermesPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cmd.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isRestart) HermesWarning else HermesTextPrimary
                                        )
                                        if (cmd.description.isNotBlank()) {
                                            Text(
                                                text = cmd.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HermesTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Attachment preview chips if any
            if (pendingAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pendingAttachments.forEach { att ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(att.filename, maxLines = 1) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveAttachment(att) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Attach button
                IconButton(
                    onClick = onAttachClicked,
                    enabled = !isStreaming,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        tint = HermesTextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Input TextField
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    placeholder = {
                        Text(
                            text = if (isStreaming) "Agent is responding..." else "Message Hermes...",
                            color = HermesTextMuted
                        )
                    },
                    enabled = !isStreaming,
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OnyxDarkBackground,
                        unfocusedContainerColor = OnyxDarkBackground,
                        disabledContainerColor = OnyxDarkBackground.copy(alpha = 0.5f),
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 2.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send or Stop Button
                if (isStreaming) {
                    // Red Stop Button
                    IconButton(
                        onClick = onCancelStream,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(HermesError)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Cancel stream",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Send Button
                    val canSend = inputText.isNotBlank() || pendingAttachments.isNotEmpty()
                    IconButton(
                        onClick = onSendMessage,
                        enabled = canSend,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (canSend) HermesPrimary else OnyxDarkSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send message",
                            tint = if (canSend) Color.Black else HermesTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
