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
import com.example.hermes.core.model.ChatAttachment
import com.example.hermes.theme.*

@Composable
fun ChatInputField(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelStream: () -> Unit,
    isStreaming: Boolean,
    pendingAttachments: List<ChatAttachment> = emptyList(),
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    onAttachClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = OnyxDarkSurface,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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
