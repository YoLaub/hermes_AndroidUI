package com.example.hermes.features.kanban.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.KanbanTask
import com.example.hermes.theme.*

@Composable
fun kanbanStatusColor(status: String): Color = when (status.lowercase()) {
    "triage" -> Color(0xFF64748B) // Slate
    "todo" -> Color(0xFF38BDF8) // Light Sky Blue
    "ready" -> Color(0xFFFBBF24) // Warm Amber
    "running" -> Color(0xFFA855F7) // Purple
    "blocked" -> Color(0xFFF43F5E) // Rose/Red
    "done" -> Color(0xFF10B981) // Emerald Green
    else -> Color(0xFF94A3B8)
}

@Composable
fun kanbanStatusLabel(status: String): String = when (status.lowercase()) {
    "triage" -> "Triage"
    "todo" -> "À faire"
    "ready" -> "Prêt"
    "running" -> "En cours"
    "blocked" -> "Bloqué"
    "done" -> "Terminé"
    "archived" -> "Archivé"
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
fun KanbanTaskCard(
    task: KanbanTask,
    onClick: () -> Unit,
    onQuickMove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = kanbanStatusColor(task.status)

    // Running pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = OnyxDarkSurfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (task.status == "running") statusColor.copy(alpha = pulseAlpha) else OnyxBorder
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .intrinsicMinHeight()
        ) {
            // Left color status stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                // Top row: ID, Priority, and Assignee
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${task.id.take(6)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Priority chip
                        val (prioText, prioBg, prioColor) = when {
                            task.priority >= 2 -> Triple("P0 Urgent", Color(0xFFEF4444).copy(alpha = 0.2f), Color(0xFFF87171))
                            task.priority == 1 -> Triple("P1 Élevée", Color(0xFFF59E0B).copy(alpha = 0.2f), Color(0xFFFBBF24))
                            task.priority == 0 -> Triple("P2 Normale", Color(0xFF3B82F6).copy(alpha = 0.2f), Color(0xFF60A5FA))
                            else -> Triple("P3 Basse", Color(0xFF6B7280).copy(alpha = 0.2f), Color(0xFF9CA3AF))
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = prioBg
                        ) {
                            Text(
                                text = prioText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = prioColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Assignee
                    task.assignee?.let { assignee ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(OnyxDarkBackground)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = HermesSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = assignee,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = HermesTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = HermesTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Optional description preview
                task.body?.let { desc ->
                    if (desc.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom row: Action Buttons / Transition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = kanbanStatusLabel(task.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }

                    // Right quick transition button
                    when (task.status) {
                        "triage" -> {
                            FilledTonalButton(
                                onClick = { onQuickMove("todo") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("À faire", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                        "todo" -> {
                            FilledTonalButton(
                                onClick = { onQuickMove("ready") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFFBBF24).copy(alpha = 0.2f),
                                    contentColor = Color(0xFFFBBF24)
                                )
                            ) {
                                Text("Prêt", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                        "ready" -> {
                            Text(
                                text = "En attente dispatch",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFBBF24)
                            )
                        }
                        "running" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFA855F7)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Agent actif",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA855F7)
                                )
                            }
                        }
                        "blocked" -> {
                            FilledTonalButton(
                                onClick = { onQuickMove("ready") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                    contentColor = Color(0xFF10B981)
                                )
                            ) {
                                Text("Débloquer", fontSize = 11.sp)
                            }
                        }
                        "done" -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.intrinsicMinHeight(): Modifier = this.height(IntrinsicSize.Min)
