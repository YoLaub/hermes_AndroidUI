package com.example.hermes.features.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.BadgeTone
import com.example.hermes.ui.components.CountBadge
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.StatusChip
import com.example.hermes.ui.components.StatusTone

data class SpaceHubState(
    val tasks: TasksSummary,
    val board: String,
    val notes: NotesSummary,
    val agenda: AgendaSummary,
)

/** Espace: four entries with what is useful at a glance, and the two most common actions. */
@Composable
fun WorkspaceHubScreen(
    state: SpaceHubState,
    onOpenTasks: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenForum: () -> Unit,
    onDictate: () -> Unit,
    onNewTask: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_space), subtitle = stringResource(R.string.space_subtitle))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HubCard(stringResource(R.string.space_tasks), Icons.Outlined.ViewKanban, onOpenTasks) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(
                        stringResource(R.string.space_tasks_blocked, state.tasks.blocked),
                        tone = if (state.tasks.blocked > 0) StatusTone.Danger else StatusTone.Neutral,
                    )
                    StatusChip(stringResource(R.string.space_tasks_running, state.tasks.running), tone = StatusTone.Info)
                }
                Text(stringResource(R.string.space_tasks_board, state.board), style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta)
            }
            HubCard(stringResource(R.string.space_notes), Icons.Outlined.Description, onOpenNotes, count = state.notes.count) {
                Text(
                    state.notes.pinnedTitle?.let { stringResource(R.string.space_notes_pinned, it) } ?: stringResource(R.string.space_notes_none_pinned),
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesColors.OnSurfaceVariant,
                )
            }
            HubCard(stringResource(R.string.space_agenda), Icons.Outlined.CalendarMonth, onOpenAgenda) {
                Text(
                    if (state.agenda.todayCount == 0) stringResource(R.string.space_agenda_none)
                    else stringResource(R.string.space_agenda_today, state.agenda.todayCount, state.agenda.next.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesColors.OnSurfaceVariant,
                )
            }
            HubCard(stringResource(R.string.space_forum), Icons.Outlined.Forum, onOpenForum) {
                Text(stringResource(R.string.space_forum_sub), style = MaterialTheme.typography.bodySmall, color = HermesColors.OnSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(stringResource(R.string.space_dictate), onClick = onDictate, variant = ButtonVariant.Secondary, icon = Icons.Outlined.Mic, modifier = Modifier.weight(1f))
                AppButton(stringResource(R.string.space_new_task), onClick = onNewTask, icon = Icons.Outlined.Add, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HubCard(title: String, icon: ImageVector, onClick: () -> Unit, count: Int = 0, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.Surface, shape)
            .border(1.dp, HermesColors.Outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).background(HermesColors.PrimaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = HermesColors.OnPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                CountBadge(count, tone = BadgeTone.Tally)
            }
            content()
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HermesColors.Meta)
    }
}

/** A section of Espace pushed as its own screen, with the section's own content below the header. */
@Composable
fun SpaceSectionScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, subtitle = stringResource(R.string.nav_space), onBack = onBack)
        Box(Modifier.weight(1f)) { content() }
    }
}
