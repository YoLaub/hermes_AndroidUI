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
import com.example.hermes.features.workspace.eventFormError
import com.example.hermes.core.model.OWNER_NAME
import com.example.hermes.ui.components.Persona
import com.example.hermes.theme.*
import java.time.LocalDate
import androidx.compose.ui.res.stringResource
import com.example.hermes.R

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EventEditorDialog(
    event: CalendarEvent,
    personas: List<Persona>,
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

    val availableProfiles = listOf(OWNER_NAME) + personas.map { it.name }

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
                        text = if (event.title.isBlank()) stringResource(R.string.agenda_nouvel_evenement_rdv) else stringResource(R.string.agenda_modifier_le_creneau),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = HermesTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.agenda_titre_de_l_evenement)) },
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
                        label = { Text(stringResource(R.string.agenda_debut_hh_mm)) },
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
                        label = { Text(stringResource(R.string.agenda_fin_hh_mm)) },
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
                    label = { Text(stringResource(R.string.agenda_lieu_ou_lien_ex_telegram)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Organizer
                Text(text = stringResource(R.string.agenda_organisateur_porteur), style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableProfiles.forEach { p ->
                        FilterChip(
                            selected = organizer == p,
                            onClick = { organizer = p },
                            label = { Text(p, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (p == OWNER_NAME) HermesPrimaryContainer else HermesTertiaryContainer,
                                selectedLabelColor = if (p == OWNER_NAME) HermesPrimary else HermesTertiary
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Participants / Assigned Profiles
                Text(text = stringResource(R.string.agenda_participants_agents_impliques), style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Text(text = stringResource(R.string.agenda_statut), style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = {
                                Text(
                                    text = when (st) {
                                        EventStatus.CONFIRMED -> stringResource(R.string.agenda_confirme_2)
                                        EventStatus.PENDING_AGENT -> stringResource(R.string.agenda_en_attente_2)
                                        EventStatus.COMPLETED -> stringResource(R.string.agenda_termine)
                                        EventStatus.CANCELLED -> stringResource(R.string.agenda_annule_2)
                                    },
                                    fontSize = 12.sp
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
                    label = { Text(stringResource(R.string.agenda_description_notes_de_reunion)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons: off with the reason shown, never a silent no-op.
                val formError = eventFormError(title, startTime, endTime)
                formError?.let {
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = HermesWarning, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = HermesTextMuted)
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
                        enabled = formError == null,
                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save), color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
