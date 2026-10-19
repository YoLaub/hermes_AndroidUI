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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewMandateCard(
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
                        label = { Text(p.name, fontSize = 12.sp) }
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
            Text("« *.site.org » couvre les sous-domaines, pas site.org lui-même : ajoute les deux si besoin.", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hosts.forEach { h -> InputChip(selected = true, onClick = { hosts = hosts - h }, label = { Text(h, fontSize = 12.sp) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer $h", modifier = Modifier.size(14.dp)) }) }
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
                folders.forEach { f -> InputChip(selected = true, onClick = { folders = folders - f }, label = { Text(f, fontSize = 12.sp) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer $f", modifier = Modifier.size(14.dp)) }) }
            }

            Text("Durée (30 jours au plus)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MANDATE_DURATIONS.forEach { d ->
                    FilterChip(selected = duration == d, onClick = { duration = d }, enabled = enabled, label = { Text(d.label, fontSize = 12.sp) })
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
