package com.example.hermes.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AVATAR_PALETTE_SIZE
import com.example.hermes.ui.components.AgentAvatar
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.AvatarSpec
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.Persona
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.defaultColorIndex
import com.example.hermes.ui.components.sanitizeInitials

private val COLOR_NAMES = listOf("Bleu", "Turquoise", "Vert", "Ambre", "Orange", "Rose", "Violet", "Gris")

/** Réglages › Avatars des agents: initials and colour, or an image, per profile; kept on this phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarScreen(
    personas: List<Persona>,
    specs: Map<String, AvatarSpec>,
    onSave: (profileId: String, spec: AvatarSpec) -> Unit,
    onImportImage: (profileId: String, uri: Uri, onDone: (String?) -> Unit) -> Unit,
    onReset: (profileId: String) -> Unit,
    onBack: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf(personas.firstOrNull()?.id) }
    val persona = personas.firstOrNull { it.id == selectedId } ?: personas.firstOrNull() ?: return
    val saved = specs[persona.id]
    // The draft follows the selected profile; it is written only on Enregistrer.
    var draft by remember(persona.id, saved) { mutableStateOf(saved ?: AvatarSpec()) }
    var useImage by remember(persona.id, saved) { mutableStateOf(saved?.image != null) }
    var importFailed by remember(persona.id) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImportImage(persona.id, uri) { name ->
            importFailed = name == null
            if (name != null) draft = draft.copy(image = name)
        }
    }
    val preview = if (useImage) draft else draft.copy(image = null)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.avatars_title), subtitle = stringResource(R.string.agent_settings), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                personas.forEach { p ->
                    FilterChip(
                        selected = p.id == persona.id,
                        onClick = { selectedId = p.id },
                        label = { Text(p.name) },
                        leadingIcon = { AgentAvatar(p, 20.dp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HermesColors.PrimaryContainer, selectedLabelColor = HermesColors.OnPrimaryContainer),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().background(HermesColors.Surface, RoundedCornerShape(12.dp)).border(1.dp, HermesColors.Outline, RoundedCornerShape(12.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AgentAvatar(persona, 88.dp, spec = preview)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.avatars_preview), style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AgentAvatar(persona, 56.dp, spec = preview)
                        AgentAvatar(persona, 40.dp, spec = preview)
                        AgentAvatar(persona, 32.dp, spec = preview)
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = if (useImage) 1 else 0, containerColor = HermesColors.Background) {
                Tab(selected = !useImage, onClick = { useImage = false }, text = { Text(stringResource(R.string.avatars_initials_tab)) })
                Tab(selected = useImage, onClick = { useImage = true }, text = { Text(stringResource(R.string.avatars_image_tab)) })
            }

            if (!useImage) {
                OutlinedTextField(
                    value = draft.initials ?: persona.initials,
                    onValueChange = { draft = draft.copy(initials = sanitizeInitials(it)) },
                    label = { Text(stringResource(R.string.avatars_initials)) },
                    supportingText = { Text(stringResource(R.string.avatars_initials_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.avatars_color), style = MaterialTheme.typography.titleSmall)
                val current = draft.color ?: defaultColorIndex(persona.id)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    (0 until AVATAR_PALETTE_SIZE).forEach { i ->
                        val selected = i == current
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(HermesColors.AvatarPalette[i], CircleShape)
                                .then(if (selected) Modifier.border(3.dp, HermesColors.OnSurface, CircleShape) else Modifier)
                                .clickable(role = Role.RadioButton) { draft = draft.copy(color = i) }
                                .semantics { contentDescription = COLOR_NAMES[i] + if (selected) ", choisie" else "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = HermesColors.Background)
                        }
                    }
                }
                Text(stringResource(R.string.avatars_color_help), style = MaterialTheme.typography.bodySmall, color = HermesColors.Meta)
            } else {
                AppButton(
                    stringResource(R.string.avatars_pick_image),
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    variant = ButtonVariant.Secondary,
                    icon = Icons.Outlined.Image,
                )
                if (importFailed) Text(stringResource(R.string.avatars_import_failed), style = MaterialTheme.typography.bodySmall, color = HermesColors.Danger)
                Text(stringResource(R.string.avatars_image_help), style = MaterialTheme.typography.bodySmall, color = HermesColors.Meta)
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(stringResource(R.string.avatars_default), onClick = { onReset(persona.id) }, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
            AppButton(stringResource(R.string.action_save), onClick = { onSave(persona.id, preview) }, modifier = Modifier.weight(1f))
        }
    }
}
