package com.example.hermes.features.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.hermes.features.profiles.*
import com.example.hermes.features.agent.ProfileSwitcherSheet
import com.example.hermes.theme.HermesColors
import kotlinx.coroutines.launch

/** Which agent tool (sheet or dialog) is open. Shared by every tab that can open one. */
class AgentToolsState {
    var profilesSheet by mutableStateOf(false)
    var createProfileDialog by mutableStateOf(false)
}

@Composable
fun rememberAgentToolsState(): AgentToolsState = remember { AgentToolsState() }

/**
 * The profile sheet and the agent dialogs, moved out of ChatScreen so the Inbox and Agent tabs
 * open the same ones. Rendered once, above every tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentToolsHost(
    viewModel: ChatViewModel,
    tools: AgentToolsState,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // The one profile switcher (Chat's title, Agent › Changer)
    if (tools.profilesSheet) {
        ModalBottomSheet(
            onDismissRequest = { tools.profilesSheet = false },
            containerColor = HermesColors.Surface
        ) {
            ProfileSwitcherSheet(
                profiles = state.profiles,
                activeProfile = state.activeProfile,
                onSelect = { name ->
                    viewModel.switchProfile(name)
                    tools.profilesSheet = false
                },
                onCreate = {
                    tools.profilesSheet = false
                    tools.createProfileDialog = true
                },
                onDelete = { name ->
                    viewModel.deleteProfile(name) { success, err ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "Profil \"$name\" supprimé"
                                else "Erreur : ${err ?: "Impossible de supprimer"}"
                            )
                        }
                    }
                },
            )
        }
    }

    // Create Profile Dialog
    if (tools.createProfileDialog) {
        CreateProfileDialog(
            existingProfiles = state.profiles,
            onDismiss = { tools.createProfileDialog = false },
            onCreate = { name, cloneFrom, model, provider, apiKey ->
                viewModel.createProfile(name, cloneFrom, model, provider, apiKey) { success, err ->
                    if (success) {
                        tools.createProfileDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Profil \"$name\" créé et activé !") }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Erreur : ${err ?: "Échec de création"}") }
                    }
                }
            }
        )
    }

}
