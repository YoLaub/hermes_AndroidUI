package com.example.hermes.features.workspace.forum.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.core.model.ForumMessage
import com.example.hermes.core.model.ReplyState
import com.example.hermes.features.chat.components.MarkdownText
import com.example.hermes.theme.*
import java.text.SimpleDateFormat
import java.util.*

/** One message of the room: the owner's, or a member's real reply, which may wait for a decision or have failed. */
@Composable
fun ForumMessageItem(
    message: ForumMessage,
    senderName: String,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.fromOwner
    val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Avatar(isUser = false)
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isUser) HermesPrimary else HermesTertiary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isUser) HermesPrimaryContainer else OnyxDarkSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(if (isUser) R.string.forum_you else R.string.forum_agent),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) HermesPrimary else HermesTextMuted,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = formattedTime, style = MaterialTheme.typography.labelSmall, color = HermesTextMuted)
            }

            Card(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(containerColor = if (isUser) HermesUserBubble else HermesAssistantBubble),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(
                        when {
                            isUser -> HermesPrimary.copy(alpha = 0.4f)
                            message.state == ReplyState.FAILED -> HermesError.copy(alpha = 0.5f)
                            message.state == ReplyState.WAITING -> HermesWarning.copy(alpha = 0.5f)
                            else -> OnyxBorder
                        }
                    )
                ),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    when (message.state) {
                        ReplyState.FAILED -> Text(
                            text = stringResource(R.string.forum_reply_failed, message.content),
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesError
                        )
                        else -> if (message.content.isNotBlank()) MarkdownText(text = message.content, color = MaterialTheme.colorScheme.onSurface)
                    }
                    val sessionId = message.sessionId
                    if (message.state == ReplyState.WAITING && sessionId != null) {
                        Text(
                            text = stringResource(R.string.forum_reply_waiting),
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesWarning,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        TextButton(onClick = { onOpenSession(sessionId) }) {
                            Text(stringResource(R.string.forum_open_session))
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            Avatar(isUser = true)
        }
    }
}

@Composable
private fun Avatar(isUser: Boolean) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isUser) OnyxDarkSurfaceVariant else HermesTertiaryContainer)
            .border(1.dp, if (isUser) OnyxBorder else HermesTertiary.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
            contentDescription = null,
            tint = if (isUser) HermesPrimary else HermesTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
