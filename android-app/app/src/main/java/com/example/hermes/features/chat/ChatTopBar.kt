package com.example.hermes.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.core.model.SessionSummary
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.ConfirmDialog

private val placeholderTitles = setOf("new chat", "untitled", "nouvelle conversation")

/** Title of the open session, or null when it has none worth showing yet. */
fun chatSessionTitle(sessions: List<SessionSummary>, sessionId: String?): String? {
    val title = sessions.firstOrNull { it.sessionId == sessionId }?.title?.trim() ?: return null
    return title.takeUnless { it.isEmpty() || it.lowercase() in placeholderTitles }
}

enum class ChatActivity { Ready, Answering }

fun chatActivity(isStreaming: Boolean): ChatActivity = if (isStreaming) ChatActivity.Answering else ChatActivity.Ready

fun profileDisplayName(profileId: String): String = profileId.replaceFirstChar { it.uppercase() }

/**
 * Chat's top bar: conversations drawer, session title with the profile line (opens the profile
 * sheet), auto-approval shield, new conversation, and a menu for the rarer actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String?,
    profileName: String,
    isStreaming: Boolean,
    yoloEnabled: Boolean,
    canDeleteSession: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenProfiles: () -> Unit,
    onToggleYolo: () -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: () -> Unit,
    onOpenAgentSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val activity = chatActivity(isStreaming)

    TopAppBar(
        windowInsets = WindowInsets.statusBars,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.Background),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.chat_conversations), tint = HermesColors.OnSurface)
            }
        },
        title = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.chat_change_profile), onClick = onOpenProfiles)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    title ?: stringResource(R.string.chat_new_conversation),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (activity == ChatActivity.Answering) HermesColors.Warning else HermesColors.Success, CircleShape)
                    )
                    Text(
                        stringResource(
                            if (activity == ChatActivity.Answering) R.string.chat_profile_answering else R.string.chat_profile_ready,
                            profileName,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = HermesColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = HermesColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleYolo) {
                Icon(
                    if (yoloEnabled) Icons.Outlined.GppMaybe else Icons.Outlined.GppGood,
                    contentDescription = stringResource(if (yoloEnabled) R.string.chat_yolo_on else R.string.chat_yolo_off),
                    tint = if (yoloEnabled) HermesColors.Warning else HermesColors.OnSurfaceVariant,
                )
            }
            IconButton(onClick = onNewSession) {
                Icon(Icons.Outlined.AddComment, contentDescription = stringResource(R.string.chat_new_conversation), tint = HermesColors.OnSurface)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more), tint = HermesColors.OnSurface)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_delete_conversation)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        enabled = canDeleteSession,
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_agent_settings)) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenAgentSettings()
                        },
                    )
                }
            }
        },
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.chat_delete_conversation_title),
            message = stringResource(R.string.chat_delete_conversation_message, title ?: stringResource(R.string.chat_new_conversation)),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDeleteSession()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

enum class YoloTap { AskToEnable, Disable }

/** Enabling auto-approval goes through a confirmation; disabling it never waits. */
fun yoloTap(enabled: Boolean): YoloTap = if (enabled) YoloTap.Disable else YoloTap.AskToEnable
