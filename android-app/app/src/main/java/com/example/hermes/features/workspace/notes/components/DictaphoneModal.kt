package com.example.hermes.features.workspace.notes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.TranscriptionState
import com.example.hermes.theme.*

@Composable
fun DictaphoneModal(
    state: TranscriptionState,
    preferOffline: Boolean,
    onToggleOffline: (Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSaveToNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HermesPrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = HermesPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Dictaphone & Transcription",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Text(
                                text = if (state.isRecording) "Enregistrement en cours..." else "Prêt pour la dictée",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.isRecording) HermesError else HermesTextMuted
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = HermesTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Online / Offline Mode Selector
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HermesCodeBackground)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !preferOffline,
                        onClick = { onToggleOffline(false) },
                        label = { Text("🌐 En ligne (Google Speech)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HermesPrimaryContainer,
                            selectedLabelColor = HermesPrimary
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = preferOffline,
                        onClick = { onToggleOffline(true) },
                        label = { Text("📴 Hors ligne (On-Device)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HermesSecondaryContainer,
                            selectedLabelColor = HermesSecondary
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Audio Wave Visualizer
                AudioWaveVisualizer(
                    isRecording = state.isRecording,
                    rmsLevel = state.rmsDb,
                    activeColor = if (state.isRecording) HermesError else HermesPrimary
                )

                // Duration timer
                Text(
                    text = "%02d:%02d".format(state.durationSec / 60, state.durationSec % 60),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (state.isRecording) HermesError else HermesTextSecondary
                    ),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Live Transcription Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HermesCodeBackground)
                        .border(1.dp, OnyxBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val transcript = buildString {
                        if (state.finalText.isNotBlank()) append(state.finalText)
                        if (state.partialText.isNotBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(state.partialText)
                        }
                    }

                    if (transcript.isNotBlank()) {
                        Text(
                            text = transcript,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = HermesTextPrimary
                        )
                    } else {
                        Text(
                            text = if (state.isRecording) "Parlez, la transcription apparaît en temps réel..." else "Appuyez sur le micro pour commencer à dicter...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HermesTextMuted
                        )
                    }
                }

                // Error Notice if any
                state.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ $err",
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesWarning
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel / Reset
                    IconButton(
                        onClick = onCancelRecording,
                        enabled = state.isRecording || state.finalText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Effacer",
                            tint = if (state.isRecording || state.finalText.isNotBlank()) HermesError else HermesTextMuted
                        )
                    }

                    // Main Record / Stop Mic Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(if (state.isRecording) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(if (state.isRecording) HermesError else HermesPrimary)
                            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (state.isRecording) onStopRecording() else onStartRecording()
                            },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (state.isRecording) "Stop" else "Enregistrer",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Save to Note Button
                    FilledTonalButton(
                        onClick = onSaveToNote,
                        enabled = state.finalText.isNotBlank() || state.partialText.isNotBlank() || state.audioFilePath != null,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = HermesSecondaryContainer,
                            contentColor = HermesSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Créer note")
                    }
                }
            }
        }
    }
}
