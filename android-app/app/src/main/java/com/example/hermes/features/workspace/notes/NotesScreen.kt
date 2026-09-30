package com.example.hermes.features.workspace.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.hermes.features.workspace.notes.components.DictaphoneModal
import com.example.hermes.features.workspace.notes.components.NoteCard
import com.example.hermes.features.workspace.notes.components.NoteEditorDialog
import com.example.hermes.theme.*

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.openDictaphoneModal()
        }
    }

    fun checkAndOpenDictaphone() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.openDictaphoneModal()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnyxDarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar & Filter Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Rechercher une note, un tag, un agent...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = HermesTextMuted
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Effacer",
                                    tint = HermesTextMuted
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder,
                        focusedContainerColor = OnyxDarkSurface,
                        unfocusedContainerColor = OnyxDarkSurface
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tags Horizontal Row
                if (state.availableTags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedTag == null,
                                onClick = { viewModel.onTagSelected(null) },
                                label = { Text("Tous (${state.notes.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HermesPrimaryContainer,
                                    selectedLabelColor = HermesPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        items(state.availableTags) { tag ->
                            FilterChip(
                                selected = state.selectedTag == tag,
                                onClick = { viewModel.onTagSelected(tag) },
                                label = { Text("#$tag") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HermesPrimaryContainer,
                                    selectedLabelColor = HermesPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            // Notes List or Empty State
            if (state.filteredNotes.isEmpty()) {
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
                            imageVector = Icons.Default.NoteAlt,
                            contentDescription = null,
                            tint = HermesTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.searchQuery.isNotBlank() || state.selectedTag != null) "Aucune note correspondante" else "Aucune note pour le moment",
                            style = MaterialTheme.typography.titleMedium,
                            color = HermesTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enregistrez un audio ou écrivez une note partagée avec vos agents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    items(state.filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            playerState = state.playerState,
                            onPlayAudio = { viewModel.playAudio(it) },
                            onPauseAudio = { viewModel.pauseAudio() },
                            onSeekAudio = { viewModel.seekAudio(it) },
                            onTogglePin = { viewModel.togglePin(note.id) },
                            onEditNote = { viewModel.openEditNote(note) },
                            onDeleteNote = { viewModel.deleteNote(note.id) }
                        )
                    }
                }
            }
        }

        // Floating Action Buttons (Record Audio & Add Note)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dictaphone Quick Record Button
            FloatingActionButton(
                onClick = { checkAndOpenDictaphone() },
                containerColor = HermesPrimaryContainer,
                contentColor = HermesPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Dictaphone",
                    modifier = Modifier.size(24.dp)
                )
            }

            // New Note Button
            ExtendedFloatingActionButton(
                onClick = { viewModel.openNewNote() },
                containerColor = HermesPrimary,
                contentColor = OnyxDarkBackground,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Note", fontWeight = FontWeight.Bold) }
            )
        }

        // Dictaphone Modal
        if (state.isRecordingModalOpen) {
            DictaphoneModal(
                state = state.transcriptionState,
                preferOffline = state.preferOfflineRecognition,
                onToggleOffline = { viewModel.toggleOfflineSpeechRecognition(it) },
                onStartRecording = { viewModel.startDictation() },
                onStopRecording = { viewModel.stopDictation() },
                onCancelRecording = { viewModel.cancelDictation() },
                onSaveToNote = { viewModel.createNoteFromTranscription() },
                onDismiss = { viewModel.closeDictaphoneModal() }
            )
        }

        // Note Editor Dialog
        if (state.isEditingNote && state.activeEditingNote != null) {
            NoteEditorDialog(
                note = state.activeEditingNote!!,
                onSave = { title, content, author, tags ->
                    viewModel.saveEditingNote(title, content, author, tags)
                },
                onDismiss = { viewModel.closeNoteEditor() }
            )
        }
    }
}
