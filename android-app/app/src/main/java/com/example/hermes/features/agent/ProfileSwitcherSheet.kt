package com.example.hermes.features.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.core.model.ProfileInfo
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.personaOf

/**
 * The one profile switcher (from Chat's title and from Agent › Changer). The active profile is
 * marked by background, check and « Actif »; a row's ⋮ deletes it, except the active and default.
 */
@Composable
fun ProfileSwitcherSheet(
    profiles: List<ProfileInfo>,
    activeProfile: String,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var toDelete by remember { mutableStateOf<ProfileInfo?>(null) }

    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.agent_change_profile), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.profiles_sheet_subtitle), style = MaterialTheme.typography.bodySmall, color = HermesColors.OnSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // No name key: the server can return two profiles with the same name.
            items(profiles) { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.name == activeProfile,
                    deletable = canDeleteProfile(profile, activeProfile, profiles),
                    onSelect = { onSelect(profile.name) },
                    onDelete = { toDelete = profile },
                )
            }
        }
        HorizontalDivider(color = HermesColors.Outline, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 8.dp)
                .clickable(onClick = onCreate)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = HermesColors.Primary)
            Text(stringResource(R.string.profiles_new), style = MaterialTheme.typography.labelLarge, color = HermesColors.Primary)
        }
    }

    toDelete?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.profiles_delete_title, personaOf(profile).name),
            message = stringResource(R.string.profiles_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                toDelete = null
                onDelete(profile.name)
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun ProfileRow(profile: ProfileInfo, active: Boolean, deletable: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val persona = personaOf(profile)
    val shape = RoundedCornerShape(12.dp)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .defaultMinSize(minHeight = 72.dp)
            .then(if (active) Modifier.background(HermesColors.PrimaryContainer, shape).border(1.5.dp, HermesColors.Primary, shape) else Modifier)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .then(
                    if (active) Modifier.background(HermesColors.Primary, CircleShape)
                    else Modifier.background(HermesColors.SurfaceVariant, CircleShape).border(1.dp, HermesColors.Outline, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(persona.initials, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (active) HermesColors.OnPrimary else HermesColors.OnSurface)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(persona.name, style = MaterialTheme.typography.titleMedium)
                if (profile.isDefault) {
                    Text(
                        stringResource(R.string.profiles_default_tag),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(HermesColors.SurfaceVariant, RoundedCornerShape(50))
                            .border(1.dp, HermesColors.Outline, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (isControlProfile(profile.name) && !active) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = HermesColors.OnSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.profiles_control_only), style = MaterialTheme.typography.bodySmall, color = HermesColors.OnSurfaceVariant)
                }
            } else {
                Text(
                    profileSubtitle(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) HermesColors.OnPrimaryContainer else HermesColors.OnSurfaceVariant,
                )
            }
        }
        when {
            active -> Row(
                Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = HermesColors.OnPrimaryContainer, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.profiles_active), style = MaterialTheme.typography.labelMedium, color = HermesColors.OnPrimaryContainer)
            }
            deletable -> Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.profiles_options, persona.name),
                        tint = HermesColors.OnSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profiles_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
