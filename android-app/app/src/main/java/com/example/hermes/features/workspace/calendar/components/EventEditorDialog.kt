package com.example.hermes.features.workspace.calendar.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.CalendarEvent
import com.example.hermes.core.model.EventStatus
import com.example.hermes.theme.*
import java.time.LocalDate

@Composable
fun EventEditorDialog(
    event: CalendarEvent,
    onSave: (
        title: String,
        description: String,
        date: LocalDate,
        startTime: String,
        endTime: String,
        organizer: String,
        assignedProfiles: List<String>,
        status: EventStatus,
        location: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(event.title) }
    var description by remember { mutableStateOf(event.description) }
    var startTime by remember { mutableStateOf(event.startTime) }
    var endTime by remember { mutableStateOf(event.endTime) }
    var location by remember { mutableStateOf(event.locationOrLink) }
    var organizer by remember { mutableStateOf(event.organizer) }
    var assignedProfiles by remember { mutableStateOf(event.assignedProfiles) }
    var status by remember { mutableStateOf(event.status) }

    val availableProfiles = listOf("Yoann", "Mario", "Gaston", "Hermes")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (event.title.isBlank()) "Nouvel Événement / RDV" else "Modifier le créneau",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = HermesTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de l'événement") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder,
                        focusedLabelColor = HermesPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Time Pickers Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Début (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Fin (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Location or Link
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lieu ou Lien (ex: Telegram / Call / Bureau)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Organizer
                Text(text = "Organisateur / Porteur :", style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableProfiles.forEach { p ->
                        FilterChip(
                            selected = organizer == p,
                            onClick = { organizer = p },
                            label = { Text(p, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (p == "Yoann") HermesPrimaryContainer else HermesTertiaryContainer,
                                selectedLabelColor = if (p == "Yoann") HermesPrimary else HermesTertiary
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Participants / Assigned Profiles
                Text(text = "Participants / Agents impliqués :", style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableProfiles.forEach { p ->
                        val isAssigned = assignedProfiles.contains(p)
                        FilterChip(
                            selected = isAssigned,
                            onClick = {
                                assignedProfiles = if (isAssigned) {
                                    assignedProfiles.filter { it != p }
                                } else {
                                    assignedProfiles + p
                                }
                            },
                            label = { Text(p, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesPrimaryContainer,
                                selectedLabelColor = HermesPrimary
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status selection
                Text(text = "Statut :", style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = {
                                Text(
                                    text = when (st) {
                                        EventStatus.CONFIRMED -> "Confirmé"
                                        EventStatus.PENDING_AGENT -> "En attente"
                                        EventStatus.COMPLETED -> "Terminé"
                                        EventStatus.CANCELLED -> "Annulé"
                                    },
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Notes de réunion") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = HermesTextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                title,
                                description,
                                LocalDate.ofEpochDay(event.dateEpochDay),
                                startTime,
                                endTime,
                                organizer,
                                assignedProfiles,
                                status,
                                location
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer", color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
