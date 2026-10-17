package com.example.hermes.features.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import com.example.hermes.core.model.MemoryAction
import com.example.hermes.core.model.MemoryValidation
import com.example.hermes.theme.*

/** A message for the user; never the raw response, which could carry text from agents or the web. */
internal fun validationErrorMessage(error: Throwable): String {
    val text = error.message.orEmpty()
    return when {
        text.contains("Jeton propriétaire non configuré") -> "Saisis d'abord le jeton propriétaire"
        text.contains("Adresse du relais non configurée") -> "Renseigne d'abord l'adresse du relais mobile"
        text.startsWith("HTTP 429") -> "Trop d'essais, réessaie dans quelques minutes"
        text.startsWith("HTTP 401") || text.startsWith("HTTP 403") -> "Jeton propriétaire refusé ou absent"
        text.startsWith("HTTP 404") -> "Élément introuvable"
        text.startsWith("HTTP 409") -> "Élément non validable"
        text.startsWith("HTTP 503") -> "Service mémoire non configuré"
        text.startsWith("HTTP 502") -> "Service mémoire injoignable"
        text.startsWith("HTTP ") -> "Erreur du service mémoire"
        else -> "Erreur réseau"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryValidationsDialog(
    items: List<MemoryValidation>,
    actions: List<MemoryAction>,
    isLoading: Boolean,
    error: String?,
    hasToken: Boolean,
    onSaveToken: (String) -> Unit,
    onRefresh: () -> Unit,
    onDecide: (eventId: String, confirm: Boolean) -> Unit,
    onDecideAction: (actionId: String, approve: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingToken by remember(hasToken) { mutableStateOf(!hasToken) }
    var tokenInput by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var toApprove by remember { mutableStateOf<MemoryAction?>(null) }

    toApprove?.let { action ->
        ApproveActionDialog(
            action = action,
            onApprove = {
                onDecideAction(action.id, true)
                toApprove = null
            },
            onCancel = { toApprove = null }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = OnyxDarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("À valider", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HermesTextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefresh, enabled = hasToken && !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = HermesPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = HermesTextPrimary)
                    }
                }

                if (editingToken) {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Jeton propriétaire") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onSaveToken(tokenInput)
                            tokenInput = ""
                        },
                        enabled = tokenInput.isNotBlank()
                    ) { Text("Enregistrer") }
                } else {
                    TextButton(onClick = { editingToken = true }) { Text("Changer le jeton") }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))

                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Actions (${actions.size})") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mémoire (${items.size})") })
                }

                val emptyTab = if (tab == 0) actions.isEmpty() else items.isEmpty()
                if (hasToken && !isLoading && error == null && emptyTab) {
                    Text("Rien en attente.", color = HermesTextPrimary, modifier = Modifier.padding(top = 12.dp))
                }

                if (tab == 0) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        items(actions, key = { it.id }) { action ->
                            ActionCard(
                                action = action,
                                enabled = !isLoading,
                                onApprove = { toApprove = action },
                                onReject = { onDecideAction(action.id, false) }
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(items, key = { it.eventId }) { item ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Plain Text only: the preview comes from agents or the web.
                                    Text(item.preview, maxLines = 8, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Origine : ${item.origin ?: "?"} • Zone : ${item.zone ?: "?"}",
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onDecide(item.eventId, true) }, enabled = !isLoading) { Text("Confirmer") }
                                        OutlinedButton(onClick = { onDecide(item.eventId, false) }, enabled = !isLoading) { Text("Rejeter") }
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

@Composable
private fun ActionCard(action: MemoryAction, enabled: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${action.profile ?: "?"} · ${actionKindLabel(action.kind)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            val reason = actionReasonText(action.reason)
            if (reason.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(reason, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            // Plain Text only: the target is what the agent asked for, possibly shaped by a web page.
            Text("Ce que l'agent demande (non fiable) :", fontSize = 11.sp)
            Text(
                action.label,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Réduire" else "Tout voir") }
            SelectionContainer {
                Text(
                    "N° ${shortId(action.id)} • ${formatRequestedAt(action.requestedAt)}\n${action.id}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = enabled) { Text("Approuver…") }
                OutlinedButton(onClick = onReject, enabled = enabled) { Text("Rejeter") }
            }
        }
    }
}

/** A second look before the owner lets an agent act: who asks, what for, and the exact request. */
@Composable
private fun ApproveActionDialog(action: MemoryAction, onApprove: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Approuver cette action ?") },
        text = {
            Column {
                Text("Profil : ${action.profile ?: "?"}", fontWeight = FontWeight.Bold)
                Text(actionKindLabel(action.kind))
                Spacer(Modifier.height(8.dp))
                Text(
                    action.label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text("N° ${shortId(action.id)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Approuver") } },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Annuler") } }
    )
}
