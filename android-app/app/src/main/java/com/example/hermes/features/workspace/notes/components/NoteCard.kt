package com.example.hermes.features.workspace.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.audio.AudioPlayerState
import com.example.hermes.core.model.AgentNote
import com.example.hermes.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteCard(
    note: AgentNote,
    playerState: AudioPlayerState,
    onPlayAudio: (String) -> Unit,
    onPauseAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit,
    onTogglePin: () -> Unit,
    onEditNote: () -> Unit,
    onDeleteNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlayingThis = playerState.isPlaying && playerState.currentPath == note.audioFilePath
    val formattedDate = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(note.updatedAt))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onEditNote() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (note.isPinned) HermesPrimary.copy(alpha = 0.6f) else OnyxBorder
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Author badge, Pin, Date, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (note.authorRole == "agent") HermesTertiaryContainer else HermesPrimaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (note.authorRole == "agent") Icons.Default.SmartToy else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (note.authorRole == "agent") HermesTertiary else HermesPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = note.author,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesTextMuted,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Épingler",
                            tint = if (note.isPinned) HermesPrimary else HermesTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteNote,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Supprimer",
                            tint = HermesTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Note Title
            Text(
                text = note.title.ifBlank { "Note sans titre" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HermesTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Note Content Preview
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = HermesTextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Audio Player Bar if audio is attached
            note.audioFilePath?.let { audioPath ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HermesCodeBackground)
                        .border(1.dp, OnyxBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isPlayingThis) onPauseAudio() else onPlayAudio(audioPath)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = if (isPlayingThis) "Pause" else "Play",
                            tint = HermesPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (isPlayingThis) {
                        Slider(
                            value = playerState.progress,
                            onValueChange = onSeekAudio,
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = HermesPrimary,
                                activeTrackColor = HermesPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "%02d:%02d".format(
                                (playerState.currentPositionMs / 1000) / 60,
                                (playerState.currentPositionMs / 1000) % 60
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = HermesTextMuted
                        )
                    } else {
                        Text(
                            text = "Enregistrement vocal",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        note.audioDurationSec?.let { dur ->
                            Text(
                                text = "%02d:%02d".format(dur / 60, dur % 60),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = HermesTextMuted
                            )
                        }
                    }
                }
            }

            // Tags Chips
            if (note.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    note.tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OnyxDarkSurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = HermesPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
