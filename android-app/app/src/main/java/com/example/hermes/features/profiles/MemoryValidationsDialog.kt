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
    isLoading: Boolean,
    error: String?,
    hasToken: Boolean,
    onSaveToken: (String) -> Unit,
    onRefresh: () -> Unit,
    onDecide: (eventId: String, confirm: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingToken by remember(hasToken) { mutableStateOf(!hasToken) }
    var tokenInput by remember { mutableStateOf("") }

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

                if (hasToken && !isLoading && error == null && items.isEmpty()) {
                    Text("Rien en attente.", color = HermesTextPrimary, modifier = Modifier.padding(top = 12.dp))
                }

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
