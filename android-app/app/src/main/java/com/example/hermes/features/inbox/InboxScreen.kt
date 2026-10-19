package com.example.hermes.features.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.core.model.ApprovalPayload
import com.example.hermes.core.model.MemoryAction
import com.example.hermes.core.model.MemoryValidation
import com.example.hermes.features.profiles.actionKindLabel
import com.example.hermes.features.profiles.actionReasonText
import com.example.hermes.features.profiles.shortId
import com.example.hermes.theme.CodeStyle
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AlertBanner
import com.example.hermes.ui.components.AlertTone
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.AppCard
import com.example.hermes.ui.components.BadgeTone
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.CardVariant
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.CountBadge
import com.example.hermes.ui.components.EmptyState
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.UntrustedText

/** What the Inbox needs from the chat state, so the screen stays a plain function of it. */
data class InboxState(
    val approval: ApprovalPayload?,
    val approvalContext: String?,
    val actions: List<MemoryAction>,
    val memory: List<MemoryValidation>,
    val memoryLoading: Boolean,
    val memoryError: String?,
    val hasOwnerToken: Boolean,
    val checkedAt: String?,
)

@Composable
fun InboxScreen(
    state: InboxState,
    onRefresh: () -> Unit,
    onRespondTool: (approved: Boolean) -> Unit,
    onOpenConversation: () -> Unit,
    onDecideAction: (id: String, approve: Boolean) -> Unit,
    onDecideMemory: (id: String, confirm: Boolean) -> Unit,
    onChangeToken: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(InboxFilter.All) }
    var toApprove by rememberSaveable { mutableStateOf<String?>(null) }
    val counts = inboxCounts(state.approval, state.actions, state.memory)
    val items = inboxItems(state.approval, state.actions, state.memory, filter)
    val firstLoad = state.memoryLoading && state.checkedAt == null

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_inbox)) {
            if (state.memoryLoading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InboxFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(stringResource(filterLabel(f))) },
                    trailingIcon = { CountBadge(counts.of(f), tone = BadgeTone.Tally) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HermesColors.PrimaryContainer,
                        selectedLabelColor = HermesColors.OnPrimaryContainer,
                    ),
                    modifier = Modifier.height(48.dp).padding(vertical = 6.dp),
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val error = state.memoryError
            if (error != null || !state.hasOwnerToken) {
                item(key = "memory-error") {
                    MemoryUnavailable(
                        message = error ?: stringResource(R.string.inbox_no_token),
                        offerToken = !state.hasOwnerToken || isOwnerTokenError(error),
                        onChangeToken = onChangeToken,
                        onRetry = onRefresh,
                    )
                }
            }
            if (firstLoad) {
                items(3) { SkeletonCard() }
                item { Text(stringResource(R.string.inbox_loading), style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant) }
            } else if (items.isEmpty() && error == null && state.hasOwnerToken) {
                item(key = "empty") {
                    val onlyFiltered = filter != InboxFilter.All && counts.total > 0
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(if (onlyFiltered) R.string.inbox_empty_filter_title else R.string.inbox_empty_title),
                        text = stringResource(if (onlyFiltered) R.string.inbox_empty_filter_text else R.string.inbox_empty_text) +
                            (state.checkedAt?.let { "\n" + stringResource(R.string.inbox_checked_at, it) } ?: ""),
                    )
                }
            }
            items(items, key = { it.key }) { item ->
                when (item) {
                    is InboxItem.Tool -> ToolCard(item.approval, state.approvalContext, onRespondTool, onOpenConversation)
                    is InboxItem.Action -> ActionCard(item.action, enabled = !state.memoryLoading, onReject = { onDecideAction(item.action.id, false) }, onApprove = { toApprove = item.action.id })
                    is InboxItem.Memory -> MemoryCard(item.item, enabled = !state.memoryLoading, onDecide = { onDecideMemory(item.item.eventId, it) })
                }
            }
        }
    }

    state.actions.firstOrNull { it.id == toApprove }?.let { action ->
        ApproveActionDialog(
            action = action,
            onApprove = {
                toApprove = null
                onDecideAction(action.id, true)
            },
            onCancel = { toApprove = null },
        )
    }
}

private fun filterLabel(f: InboxFilter) = when (f) {
    InboxFilter.All -> R.string.inbox_filter_all
    InboxFilter.Tools -> R.string.inbox_filter_tools
    InboxFilter.Actions -> R.string.inbox_filter_actions
    InboxFilter.Memory -> R.string.inbox_filter_memory
}

@Composable
private fun CardMeta(kind: String, context: String?, time: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(kind, style = MaterialTheme.typography.labelMedium, color = HermesColors.OnWarningContainer)
        if (context != null) {
            Text(context, style = MaterialTheme.typography.labelMedium, color = HermesColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (!time.isNullOrEmpty()) Text(time, style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta)
    }
}

@Composable
private fun ToolCard(approval: ApprovalPayload, context: String?, onRespond: (Boolean) -> Unit, onOpenConversation: () -> Unit) {
    AppCard(variant = CardVariant.Decision) {
        CardMeta(stringResource(R.string.inbox_kind_tool), context, null)
        val command = approval.command ?: approval.description ?: approval.tool.orEmpty()
        Text(
            command,
            style = CodeStyle,
            color = HermesColors.Code,
            modifier = Modifier
                .fillMaxWidth()
                .background(HermesColors.Background, RoundedCornerShape(8.dp))
                .padding(10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(stringResource(R.string.inbox_open_conversation), onClick = onOpenConversation, variant = ButtonVariant.Text)
            Spacer(Modifier.weight(1f))
            AppButton(stringResource(R.string.inbox_refuse), onClick = { onRespond(false) }, variant = ButtonVariant.Secondary)
            Spacer(Modifier.width(8.dp))
            AppButton(stringResource(R.string.inbox_allow), onClick = { onRespond(true) })
        }
    }
}

@Composable
internal fun ActionCard(action: MemoryAction, enabled: Boolean, onReject: () -> Unit, onApprove: () -> Unit) {
    AppCard(variant = CardVariant.Decision) {
        CardMeta(
            stringResource(R.string.inbox_kind_action, actionKindLabel(action.kind)),
            action.profile,
            "${shortId(action.id)} · ${shortTime(action.requestedAt)}",
        )
        UntrustedText(action.label)
        val reason = actionReasonText(action.reason)
        if (reason.isNotEmpty()) {
            Text(stringResource(R.string.inbox_reason, reason), style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant)
        }
        Row {
            Spacer(Modifier.weight(1f))
            AppButton(stringResource(R.string.inbox_reject), onClick = onReject, variant = ButtonVariant.Secondary, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            AppButton(stringResource(R.string.inbox_approve_ellipsis), onClick = onApprove, enabled = enabled)
        }
    }
}

@Composable
internal fun MemoryCard(item: MemoryValidation, enabled: Boolean, onDecide: (Boolean) -> Unit) {
    AppCard(variant = CardVariant.Decision) {
        CardMeta(
            stringResource(R.string.inbox_kind_memory),
            item.zone?.let { stringResource(R.string.inbox_zone, it) },
            shortTime(item.occurredAt),
        )
        // Plain Text only: the preview comes from agents or the web.
        Text("« ${item.preview} »", style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.inbox_memory_origin, item.origin ?: "?", item.source ?: "?"),
            style = MaterialTheme.typography.labelMedium,
            color = HermesColors.Meta,
        )
        Row {
            Spacer(Modifier.weight(1f))
            AppButton(stringResource(R.string.inbox_reject), onClick = { onDecide(false) }, variant = ButtonVariant.Secondary, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            AppButton(stringResource(R.string.inbox_confirm), onClick = { onDecide(true) }, enabled = enabled)
        }
    }
}

@Composable
private fun MemoryUnavailable(message: String, offerToken: Boolean, onChangeToken: () -> Unit, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AlertBanner(
            title = stringResource(R.string.inbox_memory_unavailable),
            text = message,
            tone = AlertTone.Blocking,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (offerToken) AppButton(stringResource(R.string.inbox_change_token), onClick = onChangeToken, variant = ButtonVariant.Secondary)
            AppButton(stringResource(R.string.action_retry), onClick = onRetry, variant = ButtonVariant.Text)
        }
        Text(stringResource(R.string.inbox_tools_still_available), style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant)
    }
}

@Composable
private fun SkeletonCard() {
    AppCard {
        listOf(0.4f, 0.9f, 0.6f).forEach { w ->
            Box(Modifier.fillMaxWidth(w).height(14.dp).background(HermesColors.SurfaceVariant, RoundedCornerShape(4.dp)))
        }
    }
}

/** A second look before the owner lets an agent act: who asks, what for, and the exact request. */
@Composable
internal fun ApproveActionDialog(action: MemoryAction, onApprove: () -> Unit, onCancel: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.inbox_approve_title),
        confirmLabel = stringResource(R.string.inbox_approve_action),
        onConfirm = onApprove,
        onDismiss = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RecapRow(stringResource(R.string.inbox_recap_profile), action.profile ?: "?")
            RecapRow(stringResource(R.string.inbox_recap_kind), actionKindLabel(action.kind))
            actionReasonText(action.reason).takeIf { it.isNotEmpty() }?.let { RecapRow(stringResource(R.string.inbox_recap_reason), it) }
            RecapRow(stringResource(R.string.inbox_recap_ref), "${shortId(action.id)} · ${shortTime(action.requestedAt)}")
        }
        UntrustedText(action.label)
        Text(stringResource(R.string.inbox_approve_final), style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant)
    }
}

@Composable
private fun RecapRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurface)
    }
}
