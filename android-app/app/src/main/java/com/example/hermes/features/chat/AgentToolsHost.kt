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
    var profileEnvDialog by mutableStateOf(false)
    var openBaoDialog by mutableStateOf(false)
    var skillsDialog by mutableStateOf(false)
    var memoryDialog by mutableStateOf(false)
    var validationsDialog by mutableStateOf(false)
    var memoryProfilesDialog by mutableStateOf(false)
    var mandatesDialog by mutableStateOf(false)
    var workspaceDialog by mutableStateOf(false)
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

    // Profile Env Dialog (.env variables & API keys)
    if (tools.profileEnvDialog) {
        ProfileEnvDialog(
            activeProfile = state.activeProfile,
            envEntries = state.envEntries,
            providers = state.providers,
            isLoading = state.isProvidersLoading || state.isEnvLoading,
            isEnvFallback = state.isEnvFallback,
            onSaveEnvVar = { key, value, onFinished ->
                viewModel.setProfileEnvVar(key, value) { success, errorMsg ->
                    onFinished(success, errorMsg)
                    if (success) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Variable $key enregistrée dans le .env du profil ${state.activeProfile}")
                        }
                    }
                }
            },
            onDeleteEnvVar = { key, onFinished ->
                viewModel.deleteProfileEnvVar(key) { success, errorMsg ->
                    onFinished?.invoke(success, errorMsg)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) "Variable $key supprimée du .env"
                            else "Échec de la suppression de $key : ${errorMsg ?: ""}"
                        )
                    }
                }
            },
            onSaveProviderKey = { providerId, apiKey, onFinished ->
                viewModel.setProviderKey(providerId, apiKey) { success, errorMsg ->
                    onFinished(success, errorMsg)
                    if (success) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Clé $providerId enregistrée dans le .env du profil ${state.activeProfile}")
                        }
                    }
                }
            },
            onDeleteProviderKey = { providerId, onFinished ->
                viewModel.deleteProviderKey(providerId) { success, errorMsg ->
                    onFinished?.invoke(success, errorMsg)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) "Clé $providerId supprimée du .env"
                            else "Échec de la suppression : ${errorMsg ?: ""}"
                        )
                    }
                }
            },
            onRefresh = {
                viewModel.loadProfileEnv()
                viewModel.loadProviders()
            },
            onOpenOpenbao = {
                viewModel.loadOpenbaoSecrets()
                tools.openBaoDialog = true
            },
            onDismiss = { tools.profileEnvDialog = false }
        )
    }

    // OpenBao Vault Dialog (HashiCorp / OpenBao Secrets Management)
    if (tools.openBaoDialog) {
        OpenBaoVaultDialog(
            activeProfile = state.activeProfile,
            openbaoUrl = state.openbaoUrl,
            openbaoToken = state.openbaoToken,
            openbaoMount = state.openbaoMount,
            health = state.openbaoHealth,
            secrets = state.openbaoSecrets,
            isLoading = state.isOpenbaoLoading,
            errorMessage = state.openbaoError,
            onSaveConfig = { url, token, mount, onFinished ->
                viewModel.saveOpenbaoConfig(url, token, mount, onFinished)
            },
            onSaveSecret = { key, value, onFinished ->
                viewModel.saveOpenbaoSecret(key, value, state.activeProfile) { success, err ->
                    onFinished(success, err)
                    if (success) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Secret $key enregistré dans OpenBao. Redémarrez le conteneur ${state.activeProfile} pour appliquer.")
                        }
                    }
                }
            },
            onDeleteSecret = { key, onFinished ->
                viewModel.deleteOpenbaoSecret(key, state.activeProfile) { success, err ->
                    onFinished(success, err)
                    if (success) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Secret $key supprimé d'OpenBao. Redémarrez le conteneur ${state.activeProfile} pour appliquer.")
                        }
                    }
                }
            },
            onRefresh = {
                viewModel.loadOpenbaoHealth()
                viewModel.loadOpenbaoSecrets()
            },
            onRestartGateway = {
                viewModel.restartGateway()
            },
            isRestartingGateway = state.isRestartingGateway,
            onDismiss = { tools.openBaoDialog = false }
        )
    }

    // Skills Dialog
    if (tools.skillsDialog) {
        SkillsDialog(
            profileName = state.activeProfile,
            skills = state.skills,
            isLoading = state.isSkillsLoading,
            selectedSkillDetail = state.selectedSkillDetail,
            onSelectSkill = { name -> viewModel.loadSkillDetail(name) },
            onBackToList = { viewModel.clearSkillDetail() },
            onDeleteSkill = { name -> viewModel.deleteSkill(name) },
            onDismiss = {
                tools.skillsDialog = false
                viewModel.clearSkillDetail()
            }
        )
    }

    if (tools.memoryProfilesDialog) {
        MemoryProfilesDialog(
            profiles = state.memoryProfiles,
            created = state.createdProfile,
            isLoading = state.isProfilesLoading,
            error = state.profilesError,
            hasToken = state.hasMemoryOwnerToken,
            onRefresh = { viewModel.loadMemoryProfiles() },
            onCreate = { name, read, write -> viewModel.createMemoryProfile(name, read, write) },
            onSaveGrants = { name, read, write -> viewModel.setMemoryProfileGrants(name, read, write) },
            onRevoke = { viewModel.revokeMemoryProfile(it) },
            onDismissCreated = { viewModel.dismissCreatedProfileToken() },
            onDismiss = {
                viewModel.dismissCreatedProfileToken()
                tools.memoryProfilesDialog = false
            }
        )
    }

    if (tools.mandatesDialog) {
        MemoryMandatesDialog(
            mandates = state.memoryMandates,
            profiles = state.memoryProfiles,
            isLoading = state.isMandatesLoading,
            error = state.mandatesError,
            hasToken = state.hasMemoryOwnerToken,
            onRefresh = { viewModel.loadMemoryMandates() },
            onCreate = { profile, label, hosts, folders, expiresAt, onDone ->
                viewModel.createMemoryMandate(profile, label, hosts, folders, expiresAt, onDone)
            },
            onRevoke = { viewModel.revokeMemoryMandate(it) },
            onDismiss = { tools.mandatesDialog = false }
        )
    }

    if (tools.validationsDialog) {
        MemoryValidationsDialog(
            items = state.memoryValidations,
            actions = state.memoryActions,
            isLoading = state.isValidationsLoading,
            error = state.validationsError,
            hasToken = state.hasMemoryOwnerToken,
            onSaveToken = { viewModel.saveMemoryOwnerToken(it) },
            onRefresh = {
                viewModel.loadMemoryValidations()
                viewModel.loadMemoryActions()
            },
            onDecideAction = { id, approve -> viewModel.decideMemoryAction(id, approve) },
            onDecide = { id, confirm -> viewModel.decideMemoryValidation(id, confirm) },
            onDismiss = { tools.validationsDialog = false }
        )
    }

    // Memory Dialog
    if (tools.memoryDialog) {
        MemoryDialog(
            profileName = state.activeProfile,
            memoryData = state.memoryData,
            isLoading = state.isMemoryLoading,
            onSaveSection = { section, content -> viewModel.saveMemory(section, content) },
            onDismiss = { tools.memoryDialog = false }
        )
    }

    // Workspaces Dialog
    if (tools.workspaceDialog) {
        WorkspaceDialog(
            workspaces = state.workspaces,
            activeWorkspace = state.activeWorkspace,
            isLoading = state.isWorkspacesLoading,
            onSelectWorkspace = { path ->
                viewModel.selectWorkspace(path)
                tools.workspaceDialog = false
            },
            onAddWorkspace = { path, name -> viewModel.addWorkspace(path, name) },
            onRemoveWorkspace = { path -> viewModel.removeWorkspace(path) },
            onDismiss = { tools.workspaceDialog = false }
        )
    }
}
