package com.example.hermes.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.hermes.R
import com.example.hermes.features.chat.AgentToolsState
import com.example.hermes.features.chat.ChatViewModel
import com.example.hermes.ui.components.ListRow
import com.example.hermes.ui.components.ScreenHeader

// Stand-ins that open the existing sheets and dialogs. The Inbox and Agent screens replace them.

@Composable
fun ProvisionalInboxScreen(viewModel: ChatViewModel, tools: AgentToolsState) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_inbox))
        ListRow(
            title = stringResource(R.string.inbox_memory_and_actions),
            subtitle = stringResource(R.string.inbox_memory_and_actions_sub),
            icon = Icons.Outlined.Inventory2,
            onClick = {
                viewModel.loadMemoryValidations()
                viewModel.loadMemoryActions()
                tools.validationsDialog = true
            },
        )
    }
}

@Composable
fun ProvisionalAgentScreen(tools: AgentToolsState) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_agent))
        ListRow(
            title = stringResource(R.string.agent_change_profile),
            icon = Icons.Outlined.SwapHoriz,
            onClick = { tools.profilesSheet = true },
        )
        ListRow(
            title = stringResource(R.string.agent_profile_tools),
            subtitle = stringResource(R.string.agent_profile_tools_sub),
            icon = Icons.Outlined.ManageAccounts,
            onClick = { tools.profilesSheet = true },
        )
        ListRow(
            title = stringResource(R.string.agent_settings),
            subtitle = stringResource(R.string.agent_settings_sub),
            icon = Icons.Outlined.Settings,
            onClick = { tools.settingsDialog = true },
        )
    }
}
