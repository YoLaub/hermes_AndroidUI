package com.example.hermes.features.workspace.forum.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.A2AExternalAgent
import com.example.hermes.core.model.ForumParticipant
import com.example.hermes.core.model.ParticipantType
import com.example.hermes.theme.*

@Composable
fun CreateRoomDialog(
    a2aAgents: List<A2AExternalAgent>,
    onCreate: (
        name: String,
        topic: String,
        icon: String,
        participants: List<ForumParticipant>,
        isA2AEnabled: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("🏛️") }
    var isA2AEnabled by remember { mutableStateOf(true) }

    val internalCandidates = listOf(
        ForumParticipant(name = "Mario", type = ParticipantType.INTERNAL_AGENT, roleTitle = "Commercial & Growth"),
        ForumParticipant(name = "Gaston", type = ParticipantType.INTERNAL_AGENT, roleTitle = "Tech & CRM"),
        ForumParticipant(name = "Hermes", type = ParticipantType.INTERNAL_AGENT, roleTitle = "Orchestrateur")
    )

    var selectedParticipants by remember {
        mutableStateOf(listOf(
            ForumParticipant(name = "Yoann", type = ParticipantType.USER, roleTitle = "Pilote"),
            internalCandidates[0] // Mario by default
        ))
    }

    val iconOptions = listOf("🏛️", "🚀", "💡", "🛠️", "🎯", "📊", "🤖", "🌐")

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
                        text = "Nouveau Salon Multi-Agents",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = HermesTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Emoji Icon Selector
                Text(text = "Icône du salon :", style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    iconOptions.forEach { emoji ->
                        FilterChip(
                            selected = selectedIcon == emoji,
                            onClick = { selectedIcon = emoji },
                            label = { Text(emoji, fontSize = 16.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesPrimaryContainer,
                                selectedLabelColor = HermesPrimary
                            ),
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du salon (ex: Comité Stratégique, Growth...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder,
                        focusedLabelColor = HermesPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Topic
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Objectif / Sujet de discussion") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Internal Agents Selector
                Text(text = "Inviter des agents internes :", style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    internalCandidates.forEach { candidate ->
                        val isSelected = selectedParticipants.any { it.name == candidate.name }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedParticipants = if (isSelected) {
                                    selectedParticipants.filter { it.name != candidate.name }
                                } else {
                                    selectedParticipants + candidate
                                }
                            },
                            label = { Text("🤖 ${candidate.name}", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesTertiaryContainer,
                                selectedLabelColor = HermesTertiary
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // A2A External Agents Selector
                if (a2aAgents.isNotEmpty()) {
                    Text(text = "Inviter des agents externes A2A :", style = MaterialTheme.typography.labelMedium, color = HermesSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        a2aAgents.forEach { a2a ->
                            val isSelected = selectedParticipants.any { it.name == a2a.name }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedParticipants = if (isSelected) {
                                        selectedParticipants.filter { it.name != a2a.name }
                                    } else {
                                        selectedParticipants + ForumParticipant(
                                            name = a2a.name,
                                            type = ParticipantType.EXTERNAL_A2A,
                                            roleTitle = "Agent Externe A2A",
                                            a2aEndpointUrl = a2a.endpointUrl,
                                            a2aAuthToken = a2a.authToken
                                        )
                                    }
                                },
                                label = { Text("📡 ${a2a.name} (${a2a.endpointUrl})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HermesSecondaryContainer,
                                    selectedLabelColor = HermesSecondary
                                ),
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = HermesTextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val fullName = if (name.startsWith(selectedIcon)) name else "$selectedIcon $name"
                            onCreate(fullName, topic, selectedIcon, selectedParticipants, isA2AEnabled)
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Créer le Salon", color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
