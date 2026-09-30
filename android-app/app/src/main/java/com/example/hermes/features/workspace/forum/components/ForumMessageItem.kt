package com.example.hermes.features.workspace.forum.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ForumMessage
import com.example.hermes.core.model.ParticipantType
import com.example.hermes.features.chat.components.MarkdownText
import com.example.hermes.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ForumMessageItem(
    message: ForumMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.senderType == ParticipantType.USER
    val isA2A = message.senderType == ParticipantType.EXTERNAL_A2A
    val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

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
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isA2A) HermesSecondaryContainer else HermesTertiaryContainer)
                    .border(
                        1.dp,
                        if (isA2A) HermesSecondary.copy(alpha = 0.5f) else HermesTertiary.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isA2A) Icons.Default.Language else Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (isA2A) HermesSecondary else HermesTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Sender Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        isUser -> HermesPrimary
                        isA2A -> HermesSecondary
                        else -> HermesTertiary
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Role / A2A Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isUser -> HermesPrimaryContainer
                                isA2A -> HermesSecondaryContainer
                                else -> OnyxDarkSurfaceVariant
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when {
                            isUser -> "Vous"
                            isA2A -> "A2A Externe"
                            else -> "Agent"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isUser -> HermesPrimary
                            isA2A -> HermesSecondary
                            else -> HermesTextMuted
                        },
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesTextMuted
                )
            }

            // Message Bubble Card
            Card(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isUser -> HermesUserBubble
                        isA2A -> HermesToolBackground
                        else -> HermesAssistantBubble
                    }
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        when {
                            isUser -> HermesPrimary.copy(alpha = 0.4f)
                            isA2A -> HermesSecondary.copy(alpha = 0.35f)
                            else -> OnyxBorder
                        }
                    )
                ),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // A2A Source Endpoint chip if present
                    message.a2aSourceEndpoint?.let { endpoint ->
                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(HermesCodeBackground)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = HermesSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = endpoint,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = HermesSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Markdown Content
                    MarkdownText(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            // User Avatar
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(OnyxDarkSurfaceVariant)
                    .border(1.dp, OnyxBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = HermesPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
