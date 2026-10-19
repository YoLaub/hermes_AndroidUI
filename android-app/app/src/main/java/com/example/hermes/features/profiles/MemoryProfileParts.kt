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
import androidx.compose.ui.res.stringResource
import com.example.hermes.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ZoneEditor(
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
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.memory_terminal, zone), fontSize = 12.sp) })
            }
            TOGGLE_ZONES.forEach { zone ->
                FilterChip(
                    selected = zone in selected,
                    onClick = { onToggle(zone) },
                    enabled = enabled,
                    label = { Text(zone, fontSize = 12.sp) }
                )
            }
        }
    }
}

internal fun Set<String>.toggled(zone: String): Set<String> = if (zone in this) this - zone else this + zone

@Composable
internal fun NewProfileCard(enabled: Boolean, onCreate: (String, List<String>, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var read by remember { mutableStateOf(emptySet<String>()) }
    var write by remember { mutableStateOf(emptySet<String>()) }
    val nameOk = isValidProfileName(name)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.profiles_new), fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.memory_nom_a_z_0_9)) },
                isError = name.isNotEmpty() && !nameOk,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // No grant at all is a valid profile.
            ZoneEditor("Lecture", emptyList(), read, { read = read.toggled(it) }, enabled)
            ZoneEditor("Écriture", emptyList(), write, { write = write.toggled(it) }, enabled)
            Text(stringResource(R.string.memory_secrets_et_finance_ne_se), fontSize = 12.sp)
            Button(
                onClick = { onCreate(name, grantsToSend(emptyList(), read), grantsToSend(emptyList(), write)) },
                enabled = enabled && nameOk
            ) { Text(stringResource(R.string.memory_creer)) }
        }
    }
}

/**
 * The profile's token, once. Held only by the caller's state and dropped on "done"; the window is secure (no
 * screenshot), the clipboard entry is flagged sensitive, and nothing here logs or stores it.
 */
@Composable
internal fun OneTimeTokenDialog(created: MemoryProfileCreated, onDone: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.memory_jeton_de, created.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.memory_il_ne_sera_plus_jamais), fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(created.token, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
                OutlinedButton(onClick = {
                    copySensitive(context, created.token)
                    copied = true
                }) { Text(stringResource(if (copied) R.string.memory_copied else R.string.openbao_copier)) }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.memory_c_est_copie_fermer)) } }
    )
}

private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("jeton", text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
