package com.example.hermes.features.workspace.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.features.workspace.forum.components.CreateRoomDialog
import com.example.hermes.features.workspace.forum.components.ForumInputBar
import com.example.hermes.features.workspace.forum.components.ForumMessageItem
import com.example.hermes.theme.*
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.Persona

@Composable
fun ForumScreen(
    viewModel: ForumViewModel,
    personas: List<Persona> = emptyList(),
    onOpenSession: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val nameOf = { id: String -> personas.firstOrNull { it.id == id }?.name ?: id }
    var confirmDelete by remember { mutableStateOf(false) }

    // A member that was waiting for a decision may have answered in its session meanwhile.
    LaunchedEffect(Unit) { viewModel.refreshWaiting() }

    LaunchedEffect(state.roomMessages.size) {
        if (state.roomMessages.isNotEmpty()) listState.animateScrollToItem(state.roomMessages.size - 1)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnyxDarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxDarkSurface)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.rooms, key = { it.id }) { room ->
                        val isSelected = state.selectedRoomId == room.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectRoom(room.id) },
                            label = {
                                Text(
                                    "${room.iconEmoji} ${room.name}",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesPrimaryContainer,
                                selectedLabelColor = HermesPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.openCreateRoom() }) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = stringResource(R.string.forum_new_room),
                        tint = HermesPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            state.selectedRoom?.let { room ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HermesCodeBackground)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (listOf(room.topic).filter { it.isNotBlank() } + room.members.joinToString { nameOf(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.forum_delete_room),
                            tint = HermesTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (state.roomMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            tint = HermesTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(if (state.selectedRoom == null) R.string.forum_no_room else R.string.forum_welcome),
                            style = MaterialTheme.typography.titleMedium,
                            color = HermesTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(if (state.selectedRoom == null) R.string.forum_no_room_hint else R.string.forum_welcome_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextMuted,
                            textAlign = TextAlign.Center
                        )
                        if (state.selectedRoom == null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.openCreateRoom() }) { Text(stringResource(R.string.forum_new_room)) }
                        }
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
                        ForumMessageItem(message = msg, senderName = nameOf(msg.sender), onOpenSession = onOpenSession)
                    }
                }
            }

            state.selectedRoom?.let { room ->
                ForumInputBar(
                    inputText = state.inputText,
                    members = room.members.map(nameOf),
                    onInputTextChanged = { viewModel.onInputTextChanged(it) },
                    onAppendMention = { viewModel.appendMentionToInput(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    speakingName = state.speaking?.let(nameOf)
                )
            }
        }

        if (state.isCreateRoomOpen) {
            CreateRoomDialog(
                personas = personas,
                onCreate = { name, topic, icon, members -> viewModel.createRoom(name, topic, icon, members) },
                onDismiss = { viewModel.closeCreateRoom() }
            )
        }

        val room = state.selectedRoom
        if (confirmDelete && room != null) {
            ConfirmDialog(
                title = stringResource(R.string.forum_delete_title),
                message = stringResource(R.string.forum_delete_message, room.name),
                confirmLabel = stringResource(R.string.forum_delete_room),
                destructive = true,
                onConfirm = {
                    viewModel.deleteRoom(room.id)
                    confirmDelete = false
                },
                onDismiss = { confirmDelete = false }
            )
        }
    }
}
