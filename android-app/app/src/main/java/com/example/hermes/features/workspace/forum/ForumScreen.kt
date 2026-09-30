package com.example.hermes.features.workspace.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ParticipantType
import com.example.hermes.features.workspace.forum.components.A2AAgentManagerDialog
import com.example.hermes.features.workspace.forum.components.CreateRoomDialog
import com.example.hermes.features.workspace.forum.components.ForumInputBar
import com.example.hermes.features.workspace.forum.components.ForumMessageItem
import com.example.hermes.theme.*

@Composable
fun ForumScreen(
    viewModel: ForumViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(state.roomMessages.size) {
        if (state.roomMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.roomMessages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnyxDarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Rooms Selector Horizontal Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxDarkSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.rooms) { room ->
                        val isSelected = state.selectedRoom?.id == room.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectRoom(room.id) },
                            label = { Text(room.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesPrimaryContainer,
                                selectedLabelColor = HermesPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // New Room Button
                IconButton(
                    onClick = { viewModel.openCreateRoom() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = "Nouveau salon",
                        tint = HermesPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // A2A External Agents Button
                IconButton(
                    onClick = { viewModel.openA2AManager() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Agents A2A",
                        tint = HermesSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Active Room Topic & Participants Bar
            state.selectedRoom?.let { room ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HermesCodeBackground)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = room.topic.ifBlank { "Salon collaboratif multi-agents" },
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextSecondary,
                            maxLines = 1
                        )
                    }

                    // Participant count & A2A badge
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        room.participants.forEach { p ->
                            val isA2A = p.type == ParticipantType.EXTERNAL_A2A
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isA2A) HermesSecondaryContainer else HermesTertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p.name.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isA2A) HermesSecondary else HermesTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Room Messages List or Empty State
            if (state.roomMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            tint = HermesTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Bienvenue dans le salon",
                            style = MaterialTheme.typography.titleMedium,
                            color = HermesTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Posez une question ou mentionnez un agent (@Mario, @Gaston, @A2A) pour démarrer la discussion de groupe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(state.roomMessages, key = { it.id }) { msg ->
                        ForumMessageItem(message = msg)
                    }
                }
            }

            // Bottom Input Bar
            state.selectedRoom?.let { room ->
                ForumInputBar(
                    inputText = state.inputText,
                    participants = room.participants,
                    onInputTextChanged = { viewModel.onInputTextChanged(it) },
                    onAppendMention = { viewModel.appendMentionToInput(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    isAgentTyping = state.isAgentTyping,
                    typingAgentName = state.typingAgentName
                )
            }
        }

        // Create Room Dialog
        if (state.isCreateRoomOpen) {
            CreateRoomDialog(
                a2aAgents = state.a2aAgents,
                onCreate = { name, topic, icon, participants, isA2A ->
                    viewModel.createRoom(name, topic, icon, participants, isA2A)
                },
                onDismiss = { viewModel.closeCreateRoom() }
            )
        }

        // A2A External Agents Manager Dialog
        if (state.isA2AManagerOpen) {
            A2AAgentManagerDialog(
                a2aAgents = state.a2aAgents,
                onSaveAgent = { viewModel.saveA2AAgent(it) },
                onDeleteAgent = { viewModel.deleteA2AAgent(it) },
                onDismiss = { viewModel.closeA2AManager() }
            )
        }
    }
}
