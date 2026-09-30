package com.example.hermes.features.workspace.forum.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ForumParticipant
import com.example.hermes.core.model.ParticipantType
import com.example.hermes.theme.*

@Composable
fun ForumInputBar(
    inputText: String,
    participants: List<ForumParticipant>,
    onInputTextChanged: (String) -> Unit,
    onAppendMention: (String) -> Unit,
    onSendMessage: () -> Unit,
    isAgentTyping: Boolean,
    typingAgentName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OnyxDarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Typing Indicator if any
        if (isAgentTyping && typingAgentName != null) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = HermesTertiary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$typingAgentName est en train de répondre...",
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesTertiary,
                    fontSize = 11.sp
                )
            }
        }

        // Quick Mention Shortcuts Row
        val agentParticipants = participants.filter { it.type != ParticipantType.USER }
        if (agentParticipants.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                items(agentParticipants) { p ->
                    val isA2A = p.type == ParticipantType.EXTERNAL_A2A
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isA2A) HermesSecondaryContainer else HermesTertiaryContainer)
                            .clickable { onAppendMention(p.name) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "@${p.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isA2A) HermesSecondary else HermesTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Input Field & Send Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                placeholder = { Text("Écrivez au groupe (ou @Agent)...", fontSize = 13.sp) },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HermesPrimary,
                    unfocusedBorderColor = OnyxBorder,
                    focusedContainerColor = HermesCodeBackground,
                    unfocusedContainerColor = HermesCodeBackground
                )
            )

            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(46.dp),
                containerColor = HermesPrimary,
                contentColor = OnyxDarkBackground,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Envoyer",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
