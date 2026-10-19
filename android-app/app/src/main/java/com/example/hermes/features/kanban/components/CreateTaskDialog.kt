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
import com.example.hermes.ui.components.AppButton
import androidx.compose.ui.res.stringResource
import com.example.hermes.R

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
                text = stringResource(R.string.space_new_task),
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
                    label = { Text(stringResource(R.string.kanban_titre_de_la_tache)) },
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
                    label = { Text(stringResource(R.string.kanban_details_instructions)) },
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
                Text(stringResource(R.string.kanban_colonne_initiale), fontSize = 12.sp, color = HermesTextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("triage", "todo", "ready").forEach { st ->
                        val isSelected = status == st
                        FilterChip(
                            selected = isSelected,
                            onClick = { status = st },
                            label = { Text(kanbanStatusLabel(st), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = kanbanStatusColor(st).copy(alpha = 0.25f),
                                selectedLabelColor = kanbanStatusColor(st)
                            )
                        )
                    }
                }

                // Priority
                Text(stringResource(R.string.kanban_priorite_2), fontSize = 12.sp, color = HermesTextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(2 to "P0", 1 to "P1", 0 to "P2", -1 to "P3").forEach { (prio, label) ->
                        FilterChip(
                            selected = priority == prio,
                            onClick = { priority = prio },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = assignee,
                    onValueChange = { assignee = it },
                    label = { Text(stringResource(R.string.kanban_assigne_a_optionnel)) },
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
            AppButton(
                "Créer",
                onClick = { onCreate(title.trim(), body.ifBlank { null }, status, priority, assignee.ifBlank { null }) },
                enabled = title.isNotBlank(),
                icon = Icons.Default.Add,
                disabledReason = "Donnez un titre à la tâche"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = OnyxDarkSurface
    )
}
