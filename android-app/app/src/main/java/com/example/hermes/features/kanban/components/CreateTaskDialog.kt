package com.example.hermes.features.kanban.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.theme.*

@Composable
fun CreateTaskDialog(
    initialStatus: String = "todo",
    onDismiss: () -> Unit,
    onCreate: (title: String, body: String?, status: String, priority: Int, assignee: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(initialStatus) }
    var priority by remember { mutableIntStateOf(0) }
    var assignee by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nouvelle tâche Kanban",
                style = MaterialTheme.typography.titleMedium,
                color = HermesTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de la tâche *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OnyxDarkBackground,
                        unfocusedContainerColor = OnyxDarkBackground,
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Détails / Instructions") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OnyxDarkBackground,
                        unfocusedContainerColor = OnyxDarkBackground,
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                // Status Chips
                Text("Colonne initiale :", fontSize = 12.sp, color = HermesTextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("triage", "todo", "ready").forEach { st ->
                        val isSelected = status == st
                        FilterChip(
                            selected = isSelected,
                            onClick = { status = st },
                            label = { Text(kanbanStatusLabel(st), fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = kanbanStatusColor(st).copy(alpha = 0.25f),
                                selectedLabelColor = kanbanStatusColor(st)
                            )
                        )
                    }
                }

                // Priority
                Text("Priorité :", fontSize = 12.sp, color = HermesTextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(2 to "P0", 1 to "P1", 0 to "P2", -1 to "P3").forEach { (prio, label) ->
                        FilterChip(
                            selected = priority == prio,
                            onClick = { priority = prio },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = assignee,
                    onValueChange = { assignee = it },
                    label = { Text("Assigné à (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OnyxDarkBackground,
                        unfocusedContainerColor = OnyxDarkBackground,
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, body.ifBlank { null }, status, priority, assignee.ifBlank { null })
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        containerColor = OnyxDarkSurface
    )
}
