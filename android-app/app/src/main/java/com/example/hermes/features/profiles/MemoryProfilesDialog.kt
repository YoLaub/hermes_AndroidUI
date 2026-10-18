package com.example.hermes.features.profiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.core.model.MemoryProfileCreated
import com.example.hermes.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryProfilesDialog(
    profiles: List<MemoryProfile>,
    created: MemoryProfileCreated?,
    isLoading: Boolean,
    error: String?,
    hasToken: Boolean,
    onRefresh: () -> Unit,
    onCreate: (name: String, read: List<String>, write: List<String>) -> Unit,
    onSaveGrants: (name: String, read: List<String>, write: List<String>) -> Unit,
    onRevoke: (name: String) -> Unit,
    onDismissCreated: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var creating by remember { mutableStateOf(false) }
    var toRevoke by remember { mutableStateOf<String?>(null) }

    created?.let { OneTimeTokenDialog(it, onDone = { creating = false; onDismissCreated() }) }

    toRevoke?.let { name ->
        AlertDialog(
            onDismissRequest = { toRevoke = null },
            title = { Text("Révoquer « $name » ?") },
            text = { Text("Son jeton cessera de fonctionner. Cette action ne peut pas être annulée depuis l'app.") },
            confirmButton = {
                Button(
                    onClick = { onRevoke(name); toRevoke = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Révoquer") }
            },
            dismissButton = { OutlinedButton(onClick = { toRevoke = null }) { Text("Annuler") } }
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
                    Text("Profils et zones", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HermesTextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { creating = !creating }, enabled = hasToken && !isLoading) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau profil", tint = HermesPrimary)
                    }
                    IconButton(onClick = onRefresh, enabled = hasToken && !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = HermesPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = HermesTextPrimary)
                    }
                }

                if (!hasToken) {
                    Text(
                        "Saisis d'abord le jeton propriétaire dans « Mémoire : à valider ».",
                        color = HermesTextPrimary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                // The service's own wording, as is: editing off, or a zone only the admin gives.
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    if (creating && hasToken) {
                        item { NewProfileCard(enabled = !isLoading, onCreate = onCreate) }
                    }
                    items(profiles, key = { it.name }) { profile ->
                        ProfileCard(
                            profile = profile,
                            enabled = !isLoading,
                            onSaveGrants = onSaveGrants,
                            onRevoke = { toRevoke = profile.name }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneEditor(
    title: String,
    held: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    enabled: Boolean
) {
    Column {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Given from the terminal only: shown, never a toggle.
            ADMIN_ONLY_ZONES.filter { it in held }.forEach { zone ->
                AssistChip(onClick = {}, enabled = false, label = { Text("$zone (terminal)", fontSize = 11.sp) })
            }
            TOGGLE_ZONES.forEach { zone ->
                FilterChip(
                    selected = zone in selected,
                    onClick = { onToggle(zone) },
                    enabled = enabled,
                    label = { Text(zone, fontSize = 11.sp) }
                )
            }
        }
    }
}

private fun Set<String>.toggled(zone: String): Set<String> = if (zone in this) this - zone else this + zone

@Composable
private fun ProfileCard(
    profile: MemoryProfile,
    enabled: Boolean,
    onSaveGrants: (String, List<String>, List<String>) -> Unit,
    onRevoke: () -> Unit
) {
    val revoked = profile.state == "revoked"
    var read by remember(profile) { mutableStateOf(profile.read.filter { it in TOGGLE_ZONES }.toSet()) }
    var write by remember(profile) { mutableStateOf(profile.write.filter { it in TOGGLE_ZONES }.toSet()) }
    val changed = read != profile.read.filter { it in TOGGLE_ZONES }.toSet() ||
        write != profile.write.filter { it in TOGGLE_ZONES }.toSet()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(if (revoked) "révoqué" else "actif", fontSize = 12.sp)
            }
            // Read-only: only the administrator's command can change the level.
            profile.gateLevel?.let { level ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Niveau de garde : ${if (level == "high") "élevé" else "normal"} (terminal)", fontSize = 11.sp) }
                )
            }
            if (revoked) {
                Text(
                    "Lecture : ${profile.read.joinToString().ifEmpty { "aucune" }} • Écriture : ${profile.write.joinToString().ifEmpty { "aucune" }}",
                    fontSize = 12.sp
                )
            } else {
                ZoneEditor("Lecture", profile.read, read, { read = read.toggled(it) }, enabled)
                ZoneEditor("Écriture", profile.write, write, { write = write.toggled(it) }, enabled)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveGrants(profile.name, grantsToSend(profile.read, read), grantsToSend(profile.write, write))
                        },
                        enabled = enabled && changed
                    ) { Text("Enregistrer") }
                    OutlinedButton(onClick = onRevoke, enabled = enabled) { Text("Révoquer…") }
                }
            }
        }
    }
}

@Composable
private fun NewProfileCard(enabled: Boolean, onCreate: (String, List<String>, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var read by remember { mutableStateOf(emptySet<String>()) }
    var write by remember { mutableStateOf(emptySet<String>()) }
    val nameOk = isValidProfileName(name)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nouveau profil", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom (a-z, 0-9, - et _)") },
                isError = name.isNotEmpty() && !nameOk,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // No grant at all is a valid profile.
            ZoneEditor("Lecture", emptyList(), read, { read = read.toggled(it) }, enabled)
            ZoneEditor("Écriture", emptyList(), write, { write = write.toggled(it) }, enabled)
            Text("secrets et finance ne se donnent que depuis le terminal.", fontSize = 11.sp)
            Button(
                onClick = { onCreate(name, grantsToSend(emptyList(), read), grantsToSend(emptyList(), write)) },
                enabled = enabled && nameOk
            ) { Text("Créer") }
        }
    }
}

/**
 * The profile's token, once. Held only by the caller's state and dropped on "done"; the window is secure (no
 * screenshot), the clipboard entry is flagged sensitive, and nothing here logs or stores it.
 */
@Composable
private fun OneTimeTokenDialog(created: MemoryProfileCreated, onDone: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Jeton de « ${created.name} »") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Il ne sera plus jamais affiché. Copie-le maintenant.", fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(created.token, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
                OutlinedButton(onClick = {
                    copySensitive(context, created.token)
                    copied = true
                }) { Text(if (copied) "Copié" else "Copier") }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("C'est copié, fermer") } }
    )
}

private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("jeton", text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
