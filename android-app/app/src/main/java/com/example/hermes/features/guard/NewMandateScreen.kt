package com.example.hermes.features.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.features.profiles.MANDATE_DURATIONS
import com.example.hermes.features.profiles.MANDATE_LABEL_MAX
import com.example.hermes.features.profiles.MANDATE_NOT_COVERED
import com.example.hermes.features.profiles.mandateExpiresAt
import com.example.hermes.features.profiles.mandateFormIssue
import com.example.hermes.features.profiles.parseMandateFolder
import com.example.hermes.features.profiles.parseMandateHost
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AlertBanner
import com.example.hermes.ui.components.AlertTone
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.initialsOf

/**
 * New mandate for the active profile. Sites and folders are checked as typed (the same rules as
 * before); a refusal from the server keeps everything as typed and shows its reason above.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewMandateScreen(
    activeProfile: String,
    profileName: String,
    memoryProfiles: List<MemoryProfile>,
    isLoading: Boolean,
    error: String?,
    onCreate: (profile: String, label: String, hosts: List<String>, folders: List<String>, expiresAt: String) -> Unit,
    onBack: () -> Unit,
) {
    val profile = mandateProfileFor(activeProfile, memoryProfiles)
    var label by rememberSaveable { mutableStateOf("") }
    var hosts by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var folders by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var hostDraft by rememberSaveable { mutableStateOf("") }
    var folderDraft by rememberSaveable { mutableStateOf("") }
    var addingFolder by rememberSaveable { mutableStateOf(false) }
    var durationIndex by rememberSaveable { mutableStateOf(1) }
    val duration = MANDATE_DURATIONS[durationIndex]

    // Checked as typed; the leading "*." asks for the subdomains.
    val hostParse = hostDraft.takeIf { it.isNotBlank() }?.let { parseMandateHost(it, subdomains = false) }
    val folderParse = folderDraft.takeIf { it.isNotBlank() }?.let { parseMandateFolder(it) }
    val issue = mandateFormIssue(profile, label, hosts, folders)
    val block = mandateBlock(hostParse?.error, issue?.message)

    fun addHost() {
        hostParse?.host?.let { if (it !in hosts) hosts = hosts + it; hostDraft = "" }
    }
    fun addFolder() {
        folderParse?.folder?.let { if (it !in folders) folders = folders + it; folderDraft = ""; addingFolder = false }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            stringResource(R.string.guard_new_mandate),
            subtitle = stringResource(R.string.mandate_breadcrumb),
            onBack = onBack,
        )
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // The server's refusal, translated (never the raw body); the form below is kept.
            error?.let {
                AlertBanner(it, tone = AlertTone.Blocking, title = stringResource(R.string.mandate_refused))
            }

            // Locked to the active profile.
            Row(
                Modifier.fillMaxWidth().background(HermesColors.Surface, RoundedCornerShape(12.dp)).border(1.dp, HermesColors.Outline, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).background(HermesColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initialsOf(profileName), color = HermesColors.OnPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(stringResource(R.string.mandate_profile), style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta)
                    Text(stringResource(R.string.mandate_profile_active, profileName), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (profile == null && !isLoading) {
                AlertBanner(stringResource(R.string.mandate_no_memory_profile, profileName), tone = AlertTone.Blocking)
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(MANDATE_LABEL_MAX + 20) },
                label = { Text(stringResource(R.string.mandate_label)) },
                isError = label.trim().length > MANDATE_LABEL_MAX,
                supportingText = { Text("${label.trim().length} / $MANDATE_LABEL_MAX", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mandate_sites), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hosts.forEach { h ->
                        InputChip(
                            selected = true,
                            onClick = { hosts = hosts - h },
                            label = {
                                Column {
                                    Text(h, style = MaterialTheme.typography.labelLarge)
                                    Text(hostScope(h), style = MaterialTheme.typography.labelMedium, color = HermesColors.OnSurfaceVariant)
                                }
                            },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.mandate_remove, h), modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = hostDraft,
                    onValueChange = { hostDraft = it },
                    placeholder = { Text("site.org ou *.site.org") },
                    isError = hostParse?.error != null,
                    supportingText = { Text(hostParse?.error ?: stringResource(R.string.mandate_sites_help)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { addHost() }),
                    trailingIcon = {
                        IconButton(onClick = { addHost() }, enabled = hostParse?.host != null) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.mandate_add_site))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mandate_folders), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.forEach { f ->
                        InputChip(
                            selected = true,
                            onClick = { folders = folders - f },
                            label = { Text(f, style = MaterialTheme.typography.labelLarge) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.mandate_remove, f), modifier = Modifier.size(16.dp)) },
                        )
                    }
                    if (!addingFolder) {
                        AppButton(stringResource(R.string.mandate_add_folder), onClick = { addingFolder = true }, variant = ButtonVariant.Text, icon = Icons.Outlined.Add)
                    }
                }
                if (addingFolder) {
                    OutlinedTextField(
                        value = folderDraft,
                        onValueChange = { folderDraft = it },
                        placeholder = { Text("/home/john/projet") },
                        isError = folderParse?.error != null,
                        supportingText = folderParse?.error?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { addFolder() }),
                        trailingIcon = {
                            IconButton(onClick = { addFolder() }, enabled = folderParse?.folder != null) {
                                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.mandate_add_folder))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mandate_duration), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MANDATE_DURATIONS.forEachIndexed { i, d ->
                        FilterChip(
                            selected = durationIndex == i,
                            onClick = { durationIndex = i },
                            label = { Text(d.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesColors.PrimaryContainer,
                                selectedLabelColor = HermesColors.OnPrimaryContainer,
                            ),
                        )
                    }
                }
                Text(
                    stringResource(R.string.mandate_expires, expiryLabel(System.currentTimeMillis(), duration)),
                    style = MaterialTheme.typography.labelMedium,
                    color = HermesColors.Meta,
                )
            }

            Column(
                Modifier.fillMaxWidth().background(HermesColors.WarningContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.mandate_never_covers), style = MaterialTheme.typography.labelLarge, color = HermesColors.OnWarningContainer)
                Text(MANDATE_NOT_COVERED.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = HermesColors.OnWarningContainer)
            }
        }

        AppButton(
            stringResource(R.string.mandate_create),
            onClick = {
                // The end is computed at the tap; the server decides what "now" is and may still refuse.
                profile?.let { onCreate(it, label, hosts, folders, mandateExpiresAt(System.currentTimeMillis(), duration)) }
            },
            enabled = block == null && !isLoading,
            loading = isLoading,
            disabledReason = when (block) {
                MandateBlock.FixSite -> stringResource(R.string.mandate_fix_site)
                is MandateBlock.Issue -> block.message
                null -> null
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
