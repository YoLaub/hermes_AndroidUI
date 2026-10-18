package com.example.hermes.features.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.MemoryMandate
import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.theme.*

/** Said on the create screen, before anything is entrusted: a mandate never widens any of this. */
internal val MANDATE_NOT_COVERED = listOf(
    "les commandes shell",
    "l'envoi de messages",
    "l'argent (paiements, achats)",
    "les suppressions",
    "les secrets et la finance",
    "les zones données au profil : elles restent telles que réglées sur le profil"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryMandatesDialog(
    mandates: List<MemoryMandate>,
    profiles: List<MemoryProfile>,
    isLoading: Boolean,
    error: String?,
    hasToken: Boolean,
    onRefresh: () -> Unit,
    onCreate: (profile: String, label: String, hosts: List<String>, folders: List<String>, expiresAt: String, onDone: () -> Unit) -> Unit,
    onRevoke: (id: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var creating by remember { mutableStateOf(false) }
    var toRevoke by remember { mutableStateOf<MemoryMandate?>(null) }
    val grouped = remember(mandates) { groupMandates(mandates) }
    val now = remember(mandates) { System.currentTimeMillis() }

    toRevoke?.let { mandate ->
        AlertDialog(
            onDismissRequest = { toRevoke = null },
            title = { Text("Révoquer ce mandat ?") },
            text = { Text("« ${mandate.label} » (profil ${mandate.profile}) cessera tout de suite. Le profil redemandera ton accord.") },
            confirmButton = {
                Button(
                    onClick = { onRevoke(mandate.id); toRevoke = null },
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
                    Text("Mandats", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HermesTextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { creating = !creating }, enabled = hasToken && !isLoading) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau mandat", tint = HermesPrimary)
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
                // The server's own wording, as is.
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    if (creating && hasToken) {
                        item {
                            NewMandateCard(
                                profiles = profiles.filter { it.state == "active" },
                                enabled = !isLoading,
                                onCreate = { profile, label, hosts, folders, expiresAt ->
                                    onCreate(profile, label, hosts, folders, expiresAt) { creating = false }
                                }
                            )
                        }
                    }
                    if (grouped.active.isNotEmpty()) {
                        item { SectionTitle("En cours") }
                        items(grouped.active, key = { it.id }) { MandateCard(it, now, !isLoading) { m -> toRevoke = m } }
                    }
                    if (grouped.ended.isNotEmpty()) {
                        item { SectionTitle("Terminés") }
                        items(grouped.ended, key = { it.id }) { MandateCard(it, now, !isLoading) { } }
                    }
                    if (mandates.isEmpty() && !isLoading && hasToken && !creating) {
                        item { Text("Aucun mandat.", color = HermesTextPrimary, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = HermesTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MandateCard(mandate: MemoryMandate, now: Long, enabled: Boolean, onRevoke: (MemoryMandate) -> Unit) {
    val active = mandate.state == "active"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The label is the owner's own text, shown as plain text.
                Text(mandate.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(
                    when (mandate.state) {
                        "active" -> mandateTimeLeft(mandate.expiresAt, now).let { if (it.isEmpty()) "actif" else "reste $it" }
                        "revoked" -> "révoqué"
                        else -> "expiré"
                    },
                    fontSize = 12.sp
                )
            }
            Text("Profil : ${mandate.profile}", fontSize = 12.sp)
            if (mandate.hosts.isNotEmpty()) {
                Text("Sites", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mandate.hosts.forEach { AssistChip(onClick = {}, enabled = false, label = { Text(it, fontSize = 11.sp) }) }
                }
            }
            if (mandate.folders.isNotEmpty()) {
                Text("Dossiers", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mandate.folders.forEach { AssistChip(onClick = {}, enabled = false, label = { Text(it, fontSize = 11.sp) }) }
                }
            }
            if (active) {
                OutlinedButton(onClick = { onRevoke(mandate) }, enabled = enabled) { Text("Révoquer…") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewMandateCard(
    profiles: List<MemoryProfile>,
    enabled: Boolean,
    onCreate: (String, String, List<String>, List<String>, String) -> Unit
) {
    var profile by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var hosts by remember { mutableStateOf(emptyList<String>()) }
    var folders by remember { mutableStateOf(emptyList<String>()) }
    var hostInput by remember { mutableStateOf("") }
    var folderInput by remember { mutableStateOf("") }
    var hostError by remember { mutableStateOf<String?>(null) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableStateOf(MANDATE_DURATIONS[2]) }
    val issue = mandateFormIssue(profile, label, hosts, folders)

    fun addHost(subdomains: Boolean) {
        val parsed = parseMandateHost(hostInput, subdomains)
        hostError = parsed.error
        parsed.host?.let { if (it !in hosts) hosts = hosts + it; hostInput = "" }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nouveau mandat", fontWeight = FontWeight.Bold)
            Text(
                "Tu confies à un seul profil, pour une tâche, des sites et des dossiers jusqu'à une date. " +
                    "Dedans, ce profil peut écrire et ouvrir ces sites seul, même après avoir lu le web.",
                fontSize = 12.sp
            )
            Text("Un mandat ne couvre jamais :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            MANDATE_NOT_COVERED.forEach { Text("• $it", fontSize = 12.sp) }
            Text("Pour tout cela, le profil te redemande, comme avant.", fontSize = 12.sp)

            Text("Profil", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (profiles.isEmpty()) Text("Aucun profil actif.", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                profiles.forEach { p ->
                    FilterChip(
                        selected = profile == p.name,
                        onClick = { profile = p.name },
                        enabled = enabled,
                        label = { Text(p.name, fontSize = 11.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(MANDATE_LABEL_MAX + 20) },
                label = { Text("Libellé (1 à 100 caractères)") },
                isError = label.isNotEmpty() && label.trim().length > MANDATE_LABEL_MAX,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Sites", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it; hostError = null },
                label = { Text("site.org") },
                isError = hostError != null,
                supportingText = hostError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { addHost(false) }, enabled = enabled && hostInput.isNotBlank()) { Text("Ce site", fontSize = 12.sp) }
                OutlinedButton(onClick = { addHost(true) }, enabled = enabled && hostInput.isNotBlank()) { Text("Ses sous-domaines", fontSize = 12.sp) }
            }
            Text("« *.site.org » couvre les sous-domaines, pas site.org lui-même : ajoute les deux si besoin.", fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hosts.forEach { h -> InputChip(selected = true, onClick = { hosts = hosts - h }, label = { Text(h, fontSize = 11.sp) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer $h", modifier = Modifier.size(14.dp)) }) }
            }

            Text("Dossiers", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = folderInput,
                onValueChange = { folderInput = it; folderError = null },
                label = { Text("/chemin/absolu") },
                isError = folderError != null,
                supportingText = folderError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    val parsed = parseMandateFolder(folderInput)
                    folderError = parsed.error
                    parsed.folder?.let { if (it !in folders) folders = folders + it; folderInput = "" }
                },
                enabled = enabled && folderInput.isNotBlank()
            ) { Text("Ajouter le dossier", fontSize = 12.sp) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                folders.forEach { f -> InputChip(selected = true, onClick = { folders = folders - f }, label = { Text(f, fontSize = 11.sp) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer $f", modifier = Modifier.size(14.dp)) }) }
            }

            Text("Durée (30 jours au plus)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MANDATE_DURATIONS.forEach { d ->
                    FilterChip(selected = duration == d, onClick = { duration = d }, enabled = enabled, label = { Text(d.label, fontSize = 11.sp) })
                }
            }

            issue?.let { Text(it.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    // The end is computed at the tap; the server decides what "now" is and may still refuse.
                    onCreate(profile!!, label, hosts, folders, mandateExpiresAt(System.currentTimeMillis(), duration))
                },
                enabled = enabled && issue == null
            ) { Text("Créer le mandat") }
        }
    }
}
