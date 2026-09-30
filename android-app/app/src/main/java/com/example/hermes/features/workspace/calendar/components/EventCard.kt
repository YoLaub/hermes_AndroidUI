package com.example.hermes.features.workspace.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.CalendarEvent
import com.example.hermes.core.model.EventStatus
import com.example.hermes.theme.*

@Composable
fun EventCard(
    event: CalendarEvent,
    onToggleStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                when (event.status) {
                    EventStatus.CONFIRMED -> HermesSecondary.copy(alpha = 0.4f)
                    EventStatus.PENDING_AGENT -> HermesWarning.copy(alpha = 0.5f)
                    EventStatus.COMPLETED -> OnyxBorder
                    EventStatus.CANCELLED -> HermesError.copy(alpha = 0.4f)
                }
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Time chip & Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time Range
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HermesCodeBackground)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = HermesPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${event.startTime} - ${event.endTime}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = HermesPrimary
                    )
                }

                // Status Badge (Clickable to toggle)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (event.status) {
                                EventStatus.CONFIRMED -> HermesSecondaryContainer
                                EventStatus.PENDING_AGENT -> HermesWarningContainer
                                EventStatus.COMPLETED -> OnyxDarkSurfaceVariant
                                EventStatus.CANCELLED -> HermesErrorContainer
                            }
                        )
                        .clickable { onToggleStatus() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when (event.status) {
                            EventStatus.CONFIRMED -> "✅ Confirmé"
                            EventStatus.PENDING_AGENT -> "⏳ En attente"
                            EventStatus.COMPLETED -> "🏁 Terminé"
                            EventStatus.CANCELLED -> "❌ Annulé"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (event.status) {
                            EventStatus.CONFIRMED -> HermesSecondary
                            EventStatus.PENDING_AGENT -> HermesWarning
                            EventStatus.COMPLETED -> HermesTextMuted
                            EventStatus.CANCELLED -> HermesError
                        },
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HermesTextPrimary
            )

            // Description if present
            if (event.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = HermesTextSecondary
                )
            }

            // Location or link if present
            if (event.locationOrLink.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = HermesTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.locationOrLink,
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Assigned profiles & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profiles involved
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    event.assignedProfiles.forEach { profile ->
                        val isUser = profile == "Yoann"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isUser) HermesPrimaryContainer else HermesTertiaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = if (isUser) HermesPrimary else HermesTertiary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = profile,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) HermesPrimary else HermesTertiary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Delete Icon
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
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
    }
}
