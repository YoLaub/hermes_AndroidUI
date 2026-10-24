package com.example.hermes.features.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.core.model.MemoryAction
import com.example.hermes.core.model.MemoryMandate
import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.core.model.MemoryValidation
import com.example.hermes.features.inbox.ActionCard
import com.example.hermes.features.inbox.ApproveActionDialog
import com.example.hermes.features.inbox.MemoryCard
import com.example.hermes.features.profiles.TOGGLE_ZONES
import com.example.hermes.features.profiles.grantsToSend
import com.example.hermes.features.profiles.groupMandates
import com.example.hermes.features.profiles.mandateTimeLeft
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AlertBanner
import com.example.hermes.ui.components.AlertTone
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.AppCard
import com.example.hermes.ui.components.BadgeTone
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.CountBadge
import com.example.hermes.ui.components.EmptyState
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.StatusChip
import com.example.hermes.ui.components.StatusTone
import com.example.hermes.ui.components.initialsOf

data class GuardState(
    val memory: List<MemoryValidation>,
    val actions: List<MemoryAction>,
    val pendingLoading: Boolean,
    val pendingError: String?,
    val profiles: List<MemoryProfile>,
    val profilesLoading: Boolean,
    val profilesError: String?,
    val mandates: List<MemoryMandate>,
    val mandatesLoading: Boolean,
    val mandatesError: String?,
    val hasOwnerToken: Boolean,
)

class GuardActions(
    val onRefresh: (GuardTab) -> Unit,
    val onDecideMemory: (id: String, confirm: Boolean) -> Unit,
    val onDecideAction: (id: String, approve: Boolean) -> Unit,
    val onSaveGrants: (name: String, read: List<String>, write: List<String>) -> Unit,
    val onRevokeProfile: (String) -> Unit,
    val onNewProfile: () -> Unit,
    val onRevokeMandate: (String) -> Unit,
    val onNewMandate: () -> Unit,
    val onChangeToken: () -> Unit,
    val onBack: () -> Unit,
)

/** « Mémoire & garde »: four fixed tabs, one explaining line each. Errors are the translated memory messages. */
@Composable
fun GuardScreen(state: GuardState, actions: GuardActions, initialTab: GuardTab = GuardTab.Pending) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var toApprove by rememberSaveable { mutableStateOf<String?>(null) }
    val loading = when (tab) {
        GuardTab.Pending, GuardTab.Actions -> state.pendingLoading
        GuardTab.Profiles -> state.profilesLoading
        GuardTab.Mandates -> state.mandatesLoading
    }
    val error = when (tab) {
        GuardTab.Pending, GuardTab.Actions -> state.pendingError
        GuardTab.Profiles -> state.profilesError
        GuardTab.Mandates -> state.mandatesError
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.guard_title), subtitle = stringResource(R.string.nav_agent), onBack = actions.onBack) {
            if (tab == GuardTab.Profiles && state.hasOwnerToken) {
                IconButton(onClick = actions.onNewProfile) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.guard_new_profile))
                }
            }
            IconButton(onClick = { actions.onRefresh(tab) }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        }
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, containerColor = HermesColors.Background, edgePadding = 8.dp) {
            GuardTab.entries.forEach { t ->
                val count = when (t) {
                    GuardTab.Pending -> state.memory.size
                    GuardTab.Actions -> state.actions.size
                    else -> 0
                }
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(tabLabel(t)), style = MaterialTheme.typography.labelLarge)
                            CountBadge(count, tone = BadgeTone.Decision)
                        }
                    },
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(tabExplanation(tab)), style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant) }
            if (!state.hasOwnerToken) {
                item {
                    AlertBanner(stringResource(R.string.inbox_no_token), tone = AlertTone.Blocking)
                }
                item { AppButton(stringResource(R.string.inbox_change_token), onClick = actions.onChangeToken, variant = ButtonVariant.Secondary) }
            } else {
                if (error != null) item { AlertBanner(error, tone = AlertTone.Blocking, title = stringResource(R.string.guard_error)) }
                when (tab) {
                    GuardTab.Pending -> pendingTab(state.memory, loading, actions)
                    GuardTab.Actions -> actionsTab(state.actions, loading, actions) { toApprove = it }
                    GuardTab.Profiles -> profilesTab(state.profiles, loading, actions)
                    GuardTab.Mandates -> mandatesTab(state.mandates, loading, actions)
                }
            }
        }
        if (tab == GuardTab.Mandates && state.hasOwnerToken) {
            AppButton(
                stringResource(R.string.guard_new_mandate),
                onClick = actions.onNewMandate,
                icon = Icons.Outlined.Add,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }

    state.actions.firstOrNull { it.id == toApprove }?.let { action ->
        ApproveActionDialog(
            action = action,
            onApprove = {
                toApprove = null
                actions.onDecideAction(action.id, true)
            },
            onCancel = { toApprove = null },
        )
    }
}

private fun tabLabel(t: GuardTab) = when (t) {
    GuardTab.Pending -> R.string.guard_tab_pending
    GuardTab.Actions -> R.string.guard_tab_actions
    GuardTab.Profiles -> R.string.guard_tab_profiles
    GuardTab.Mandates -> R.string.guard_tab_mandates
}

private fun tabExplanation(t: GuardTab) = when (t) {
    GuardTab.Pending -> R.string.guard_explain_pending
    GuardTab.Actions -> R.string.guard_explain_actions
    GuardTab.Profiles -> R.string.guard_explain_profiles
    GuardTab.Mandates -> R.string.guard_explain_mandates
}

private fun LazyListScope.pendingTab(memory: List<MemoryValidation>, loading: Boolean, actions: GuardActions) {
    if (memory.isEmpty() && !loading) {
        item { EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.guard_empty_pending)) }
    }
    items(memory) { item -> MemoryCard(item, enabled = !loading, onDecide = { actions.onDecideMemory(item.eventId, it) }) }
}

private fun LazyListScope.actionsTab(list: List<MemoryAction>, loading: Boolean, actions: GuardActions, onApprove: (String) -> Unit) {
    if (list.isEmpty() && !loading) {
        item { EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.guard_empty_actions)) }
    }
    items(list) { action ->
        ActionCard(action, enabled = !loading, onReject = { actions.onDecideAction(action.id, false) }, onApprove = { onApprove(action.id) })
    }
}

private fun LazyListScope.profilesTab(profiles: List<MemoryProfile>, loading: Boolean, actions: GuardActions) {
    if (profiles.isEmpty() && !loading) {
        item {
            EmptyState(
                Icons.Outlined.VerifiedUser,
                stringResource(R.string.guard_empty_profiles),
                actionLabel = stringResource(R.string.guard_new_profile),
                onAction = actions.onNewProfile,
            )
        }
    }
    items(profiles) { profile -> ProfileCard(profile, enabled = !loading, actions) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(profile: MemoryProfile, enabled: Boolean, actions: GuardActions) {
    val revoked = profile.state == "revoked"
    var read by remember(profile) { mutableStateOf(profile.read.filter { it in TOGGLE_ZONES }.toSet()) }
    var write by remember(profile) { mutableStateOf(profile.write.filter { it in TOGGLE_ZONES }.toSet()) }
    var confirmRevoke by remember { mutableStateOf(false) }
    val changed = read != profile.read.filter { it in TOGGLE_ZONES }.toSet() || write != profile.write.filter { it in TOGGLE_ZONES }.toSet()

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(HermesColors.SurfaceVariant, CircleShape).border(1.dp, HermesColors.Outline, CircleShape), contentAlignment = Alignment.Center) {
                Text(initialsOf(profile.name), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (revoked) R.string.guard_profile_revoked else R.string.guard_profile_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (revoked) HermesColors.OnDangerContainer else HermesColors.OnSuccessContainer,
                )
            }
            // Read-only: only the administrator's command can change the level.
            profile.gateLevel?.let { level ->
                StatusChip(
                    stringResource(if (level == "high") R.string.guard_gate_high else R.string.guard_gate_normal),
                    tone = StatusTone.ReadOnly,
                )
            }
        }
        if (revoked) {
            Text(
                stringResource(R.string.guard_revoked_zones, profile.read.joinToString().ifEmpty { "—" }, profile.write.joinToString().ifEmpty { "—" }),
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.OnSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guard_zone), style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.guard_read), style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta, modifier = Modifier.width(72.dp))
                Text(stringResource(R.string.guard_write), style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta, modifier = Modifier.width(72.dp))
            }
            zoneGrid(read, write).forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.zone, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    ZoneSwitch(row.read, enabled, stringResource(R.string.guard_read_zone, row.zone)) {
                        read = if (it) read + row.zone else read - row.zone
                    }
                    ZoneSwitch(row.write, enabled, stringResource(R.string.guard_write_zone, row.zone)) {
                        write = if (it) write + row.zone else write - row.zone
                    }
                }
            }
            // Given from the terminal only: a read-only pill, never a switch.
            val terminal = terminalOnlyZones(profile)
            if (terminal.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip("${terminal.joinToString(" · ")} · ${stringResource(R.string.terminal_only)}", tone = StatusTone.ReadOnly)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    stringResource(R.string.action_save),
                    onClick = { actions.onSaveGrants(profile.name, grantsToSend(profile.read, read), grantsToSend(profile.write, write)) },
                    enabled = enabled && changed,
                )
                AppButton(
                    stringResource(R.string.guard_revoke_profile),
                    onClick = { confirmRevoke = true },
                    variant = ButtonVariant.DestructiveSecondary,
                    enabled = enabled,
                )
            }
        }
    }
    if (confirmRevoke) {
        ConfirmDialog(
            title = stringResource(R.string.guard_revoke_profile_title, profile.name),
            message = stringResource(R.string.guard_revoke_profile_message),
            confirmLabel = stringResource(R.string.guard_revoke),
            destructive = true,
            onConfirm = {
                confirmRevoke = false
                actions.onRevokeProfile(profile.name)
            },
            onDismiss = { confirmRevoke = false },
        )
    }
}

@Composable
private fun ZoneSwitch(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SwitchDefaults.colors(checkedTrackColor = HermesColors.Primary, checkedThumbColor = HermesColors.Background),
        )
    }
}

private fun LazyListScope.mandatesTab(mandates: List<MemoryMandate>, loading: Boolean, actions: GuardActions) {
    item(key = "mandates") { MandatesList(mandates, loading, actions) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MandatesList(mandates: List<MemoryMandate>, loading: Boolean, actions: GuardActions) {
    val grouped = remember(mandates) { groupMandates(mandates) }
    val now = remember(mandates) { System.currentTimeMillis() }
    var showEnded by rememberSaveable { mutableStateOf(false) }
    var toRevoke by remember { mutableStateOf<MemoryMandate?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !showEnded, onClick = { showEnded = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(stringResource(R.string.guard_mandates_active, grouped.active.size))
            }
            SegmentedButton(selected = showEnded, onClick = { showEnded = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(stringResource(R.string.guard_mandates_ended, grouped.ended.size))
            }
        }
        val list = if (showEnded) grouped.ended else grouped.active
        if (list.isEmpty() && !loading) {
            EmptyState(
                Icons.Outlined.Badge,
                stringResource(if (showEnded) R.string.guard_empty_mandates_ended else R.string.guard_empty_mandates),
                actionLabel = if (showEnded) null else stringResource(R.string.guard_new_mandate),
                onAction = if (showEnded) null else actions.onNewMandate,
            )
        }
        list.forEach { mandate ->
            val active = mandate.state == "active"
            AppCard {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        // The label is the owner's own text, shown as plain text.
                        Text(mandate.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(mandate.profile.ifBlank { null }, mandate.expiresAt?.let { stringResource(R.string.guard_mandate_until, it.take(16).replace('T', ' ')) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = HermesColors.Meta,
                        )
                    }
                    StatusChip(
                        when (mandate.state) {
                            "active" -> mandateTimeLeft(mandate.expiresAt, now).ifEmpty { stringResource(R.string.guard_profile_active) }
                            "revoked" -> stringResource(R.string.guard_mandate_revoked)
                            else -> stringResource(R.string.guard_mandate_expired)
                        },
                        tone = when (mandate.state) {
                            "active" -> StatusTone.Info
                            "revoked" -> StatusTone.Danger
                            else -> StatusTone.Neutral
                        },
                    )
                }
                if (mandate.hosts.isNotEmpty() || mandate.folders.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (mandate.hosts + mandate.folders).forEach { StatusChip(it) }
                    }
                }
                if (active) {
                    AppButton(
                        stringResource(R.string.guard_revoke_ellipsis),
                        onClick = { toRevoke = mandate },
                        variant = ButtonVariant.DestructiveSecondary,
                        enabled = !loading,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    toRevoke?.let { mandate ->
        ConfirmDialog(
            title = stringResource(R.string.guard_revoke_mandate_title),
            message = stringResource(R.string.guard_revoke_mandate_message, mandate.label, mandate.profile),
            confirmLabel = stringResource(R.string.guard_revoke),
            destructive = true,
            onConfirm = {
                toRevoke = null
                actions.onRevokeMandate(mandate.id)
            },
            onDismiss = { toRevoke = null },
        )
    }
}
