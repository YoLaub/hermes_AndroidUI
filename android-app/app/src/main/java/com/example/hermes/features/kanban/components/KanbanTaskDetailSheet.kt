package com.example.hermes.features.kanban.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.KanbanTask
import com.example.hermes.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanTaskDetailSheet(
    task: KanbanTask,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String, status: String, priority: Int, assignee: String?) -> Unit,
    onBlock: (reason: String) -> Unit,
    onUnblock: () -> Unit,
    onArchive: () -> Unit
) {
    var editedTitle by remember(task) { mutableStateOf(task.title) }
    var editedBody by remember(task) { mutableStateOf(task.body ?: "") }
    var editedStatus by remember(task) { mutableStateOf(task.status) }
    var editedPriority by remember(task) { mutableIntStateOf(task.priority) }
    var editedAssignee by remember(task) { mutableStateOf(task.assignee ?: "") }

    var showBlockDialog by remember { mutableStateOf(false) }
    var blockReason by remember { mutableStateOf("En attente de ressource") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OnyxDarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: ID + Status + Archive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tâche #${task.id.take(8)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    task.ageSeconds?.let { sec ->
                        val mins = (sec / 60).toInt()
                        Text(
                            text = "Créée il y a ${if (mins < 60) "$mins min" else "${mins / 60} h"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextMuted
                        )
                    }
                }

                IconButton(onClick = onArchive) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = "Archiver",
                        tint = HermesWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title field
            OutlinedTextField(
                value = editedTitle,
                onValueChange = { editedTitle = it },
                label = { Text("Titre") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OnyxDarkBackground,
                    unfocusedContainerColor = OnyxDarkBackground,
                    focusedBorderColor = HermesPrimary,
                    unfocusedBorderColor = OnyxBorder
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description field
            OutlinedTextField(
                value = editedBody,
                onValueChange = { editedBody = it },
                label = { Text("Description / Contexte") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OnyxDarkBackground,
                    unfocusedContainerColor = OnyxDarkBackground,
                    focusedBorderColor = HermesPrimary,
                    unfocusedBorderColor = OnyxBorder
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status Selector Chips
            Text(
                text = "Statut",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = HermesTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statuses = listOf("triage", "todo", "ready", "done")
                statuses.forEach { st ->
                    val isSelected = editedStatus == st
                    val stColor = kanbanStatusColor(st)
                    FilterChip(
                        selected = isSelected,
                        onClick = { editedStatus = st },
                        label = { Text(kanbanStatusLabel(st), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = stColor.copy(alpha = 0.25f),
                            selectedLabelColor = stColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Priority Selector
            Text(
                text = "Priorité",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = HermesTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val priorities = listOf(
                    2 to "P0 Urgent",
                    1 to "P1 Haute",
                    0 to "P2 Normale",
                    -1 to "P3 Basse"
                )
                priorities.forEach { (prio, label) ->
                    val isSelected = editedPriority == prio
                    FilterChip(
                        selected = isSelected,
                        onClick = { editedPriority = prio },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HermesPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = HermesPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Assignee field
            OutlinedTextField(
                value = editedAssignee,
                onValueChange = { editedAssignee = it },
                label = { Text("Assigné à (agent ou profil)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OnyxDarkBackground,
                    unfocusedContainerColor = OnyxDarkBackground,
                    focusedBorderColor = HermesPrimary,
                    unfocusedBorderColor = OnyxBorder
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Block / Unblock actions
            if (task.status == "blocked") {
                Button(
                    onClick = onUnblock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Débloquer la tâche")
                }
            } else if (task.status != "done" && task.status != "archived") {
                OutlinedButton(
                    onClick = { showBlockDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF43F5E))
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Marquer comme bloqué")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save changes button
            Button(
                onClick = {
                    onSave(
                        editedTitle,
                        editedBody,
                        editedStatus,
                        editedPriority,
                        editedAssignee.ifBlank { null }
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enregistrer les modifications")
            }
        }
    }

    // Dialog for block reason
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Bloquer la tâche") },
            text = {
                Column {
                    Text(
                        text = "Indiquez la raison du blocage (l'agent en tiendra compte) :",
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = blockReason,
                        onValueChange = { blockReason = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBlock(blockReason)
                        showBlockDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                ) {
                    Text("Bloquer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
