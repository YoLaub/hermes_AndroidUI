package com.example.hermes.features.workspace.forum.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.A2AExternalAgent
import com.example.hermes.theme.*

@Composable
fun A2AAgentManagerDialog(
    a2aAgents: List<A2AExternalAgent>,
    onSaveAgent: (A2AExternalAgent) -> Unit,
    onDeleteAgent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isAddingNew by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var endpointInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }

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
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HermesSecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = HermesSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Agents Externes A2A",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Text(
                                text = "Protocole Agent-to-Agent Décentralisé",
                                style = MaterialTheme.typography.labelSmall,
                                color = HermesSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = HermesTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isAddingNew) {
                    // Add Form
                    Text(
                        text = "Enregistrer un agent distant :",
                        style = MaterialTheme.typography.labelMedium,
                        color = HermesTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nom de l'agent externe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesSecondary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("Endpoint A2A (URL HTTPS ou IP)") },
                        placeholder = { Text("https://agent.partner.io/v1/a2a") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesSecondary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Token d'authentification Bearer (optionnel)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesSecondary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Description & Capacités") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesSecondary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isAddingNew = false }) {
                            Text("Annuler", color = HermesTextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (nameInput.isNotBlank() && endpointInput.isNotBlank()) {
                                    val newA2A = A2AExternalAgent(
                                        name = nameInput.trim(),
                                        endpointUrl = endpointInput.trim(),
                                        authToken = tokenInput.trim(),
                                        description = descInput.trim()
                                    )
                                    onSaveAgent(newA2A)
                                    isAddingNew = false
                                    nameInput = ""
                                    endpointInput = ""
                                    tokenInput = ""
                                    descInput = ""
                                }
                            },
                            enabled = nameInput.isNotBlank() && endpointInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = HermesSecondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ajouter", color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Agent List & Add button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${a2aAgents.size} agent(s) A2A configuré(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextMuted
                        )

                        FilledTonalButton(
                            onClick = { isAddingNew = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = HermesSecondaryContainer,
                                contentColor = HermesSecondary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ajouter Agent", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(a2aAgents) { agent ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = HermesCodeBackground),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(HermesSecondary.copy(alpha = 0.3f))
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (agent.isReachable) HermesSecondary else HermesError)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = agent.name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = HermesTextPrimary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = agent.endpointUrl,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = HermesSecondary,
                                            fontSize = 10.sp
                                        )

                                        if (agent.description.isNotBlank()) {
                                            Text(
                                                text = agent.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HermesTextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onDeleteAgent(agent.id) },
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
                        }
                    }
                }
            }
        }
    }
}
