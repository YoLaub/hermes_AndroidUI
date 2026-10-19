package com.example.hermes.features.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Widgets
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
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.ListRow
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.SectionCard
import com.example.hermes.ui.components.StatusChip
import com.example.hermes.ui.components.StatusTone
import com.example.hermes.ui.components.personaOf

data class AgentState(
    val profile: ProfileInfo,
    val activeWorkspace: String?,
    val guardPending: Int,
    val isRestartingGateway: Boolean,
)

/** Agent tab: who is active, its configuration, the guard, maintenance, and the way to Réglages. */
@Composable
fun AgentScreen(
    state: AgentState,
    onChangeProfile: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onOpenEnv: () -> Unit,
    onOpenValidations: () -> Unit,
    onOpenMemoryProfiles: () -> Unit,
    onOpenMandates: () -> Unit,
    onRestartGateway: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val persona = personaOf(state.profile)
    var confirmRestart by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_agent)) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.agent_settings))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Active profile
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HermesColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, HermesColors.Outline, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(56.dp).background(HermesColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(persona.initials, color = HermesColors.OnPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(persona.name, style = MaterialTheme.typography.titleLarge)
                    Text(state.profile.model ?: stringResource(R.string.agent_default_model), style = MaterialTheme.typography.bodySmall, color = HermesColors.Code)
                    StatusChip(
                        stringResource(if (state.profile.gatewayRunning) R.string.agent_gateway_online else R.string.agent_gateway_offline),
                        tone = if (state.profile.gatewayRunning) StatusTone.Success else StatusTone.Neutral,
                    )
                }
                AppButton(stringResource(R.string.agent_change), onClick = onChangeProfile, variant = ButtonVariant.Secondary)
            }

            SectionCard(stringResource(R.string.agent_section_config)) {
                ListRow(
                    stringResource(R.string.agent_skills),
                    subtitle = stringResource(R.string.agent_skills_sub, state.profile.skillCount),
                    icon = Icons.Outlined.Widgets,
                    onClick = onOpenSkills,
                )
                ListRow(stringResource(R.string.agent_memory_soul), subtitle = "MEMORY.md · USER.md · SOUL.md", icon = Icons.Outlined.Psychology, onClick = onOpenMemory)
                ListRow(
                    stringResource(R.string.agent_workspaces),
                    subtitle = state.activeWorkspace?.let { stringResource(R.string.agent_workspaces_active, it) },
                    icon = Icons.Outlined.Folder,
                    onClick = onOpenWorkspaces,
                )
                ListRow(stringResource(R.string.agent_env), subtitle = stringResource(R.string.agent_env_sub), icon = Icons.Outlined.Key, onClick = onOpenEnv)
            }

            // Three rows until « Mémoire & garde » gathers them in one screen.
            SectionCard(stringResource(R.string.agent_section_guard)) {
                ListRow(
                    stringResource(R.string.agent_guard_pending),
                    subtitle = stringResource(R.string.agent_guard_pending_sub),
                    icon = Icons.Outlined.TaskAlt,
                    badge = state.guardPending,
                    onClick = onOpenValidations,
                )
                ListRow(stringResource(R.string.agent_guard_profiles), subtitle = stringResource(R.string.agent_guard_profiles_sub), icon = Icons.Outlined.VerifiedUser, onClick = onOpenMemoryProfiles)
                ListRow(stringResource(R.string.agent_guard_mandates), subtitle = stringResource(R.string.agent_guard_mandates_sub), icon = Icons.Outlined.Badge, onClick = onOpenMandates)
            }

            SectionCard(stringResource(R.string.agent_section_maintenance)) {
                ListRow(
                    stringResource(R.string.agent_restart, persona.name),
                    subtitle = stringResource(if (state.isRestartingGateway) R.string.agent_restarting else R.string.agent_restart_sub),
                    icon = Icons.Outlined.RestartAlt,
                    enabled = !state.isRestartingGateway,
                    onClick = { confirmRestart = true },
                )
            }
        }
    }

    if (confirmRestart) {
        ConfirmDialog(
            title = stringResource(R.string.agent_restart_title, persona.name),
            message = stringResource(R.string.agent_restart_message),
            confirmLabel = stringResource(R.string.agent_restart_confirm),
            onConfirm = {
                confirmRestart = false
                onRestartGateway()
            },
            onDismiss = { confirmRestart = false },
        )
    }
}

