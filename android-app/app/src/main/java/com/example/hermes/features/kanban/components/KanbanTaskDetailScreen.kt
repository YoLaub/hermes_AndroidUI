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
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.ScreenHeader
import androidx.compose.ui.res.stringResource
import com.example.hermes.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanTaskDetailScreen(
    task: KanbanTask,
    boardName: String,
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

    var confirmArchive by remember { mutableStateOf(false) }

    // Full screen (Espace › Tâches › Tâche); system back pops it.
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.kanban_task), subtitle = "#${task.id.take(8)} · $boardName", onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
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
                        text = stringResource(R.string.kanban_tache, task.id.take(8)),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    task.ageSeconds?.let { sec ->
                        val mins = (sec / 60).toInt()
                        Text(
                            text = if (mins < 60) stringResource(R.string.kanban_created_minutes_ago, mins) else stringResource(R.string.kanban_created_hours_ago, mins / 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextMuted
                        )
                    }
                }

                IconButton(onClick = { confirmArchive = true }) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = stringResource(R.string.kanban_archiver),
                        tint = HermesWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title field
            OutlinedTextField(
                value = editedTitle,
                onValueChange = { editedTitle = it },
                label = { Text(stringResource(R.string.kanban_titre)) },
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
                label = { Text(stringResource(R.string.kanban_description_contexte)) },
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
                text = stringResource(R.string.kanban_statut),
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
                        label = { Text(kanbanStatusLabel(st), fontSize = 12.sp) },
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
                text = stringResource(R.string.kanban_priorite),
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
                        label = { Text(label, fontSize = 12.sp) },
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
                label = { Text(stringResource(R.string.kanban_assigne_a_agent_ou_profil)) },
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
                    Text(stringResource(R.string.kanban_debloquer_la_tache))
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
                    Text(stringResource(R.string.kanban_marquer_comme_bloque))
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
                Text(stringResource(R.string.kanban_enregistrer_les_modifications))
            }
        }
    }

    // Dialog for block reason
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(R.string.kanban_bloquer_la_tache)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.kanban_indiquez_la_raison_du_blocage),
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
                    Text(stringResource(R.string.kanban_bloquer))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.kanban_archiver_cette_tache),
            message = stringResource(R.string.kanban_quitte_le_tableau_elle_reste, task.title),
            confirmLabel = stringResource(R.string.kanban_archiver),
            destructive = true,
            onConfirm = {
                confirmArchive = false
                onArchive()
                onDismiss()
            },
            onDismiss = { confirmArchive = false }
        )
    }
}
