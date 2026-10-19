package com.example.hermes.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.hermes.features.chat.AgentToolsHost
import com.example.hermes.features.chat.ChatScreen
import com.example.hermes.features.chat.ChatViewModel
import com.example.hermes.features.agent.AgentScreen
import com.example.hermes.features.agent.AgentState
import com.example.hermes.features.profiles.OpenBaoVaultScreen
import com.example.hermes.features.profiles.ProfileEnvScreen
import kotlinx.coroutines.launch
import com.example.hermes.features.guard.GuardActions
import com.example.hermes.features.guard.GuardScreen
import com.example.hermes.features.guard.GuardState
import com.example.hermes.features.guard.GuardTab
import com.example.hermes.features.guard.NewMandateScreen
import com.example.hermes.features.guard.NewMemoryProfileScreen
import com.example.hermes.features.agent.MemoryScreen
import com.example.hermes.features.agent.SkillDetailScreen
import com.example.hermes.features.agent.SkillsScreen
import com.example.hermes.features.agent.WorkspacesScreen
import com.example.hermes.features.settings.SettingsScreen
import com.example.hermes.features.settings.RelaySettingsScreen
import com.example.hermes.features.settings.openBaoStatus
import com.example.hermes.core.model.ProfileInfo
import com.example.hermes.features.chat.chatSessionTitle
import com.example.hermes.features.chat.profileDisplayName
import com.example.hermes.features.inbox.InboxScreen
import com.example.hermes.features.inbox.InboxState
import com.example.hermes.features.inbox.inboxCounts
import com.example.hermes.features.inbox.shortTime
import java.time.Instant
import com.example.hermes.features.chat.rememberAgentToolsState
import com.example.hermes.features.kanban.KanbanScreen
import com.example.hermes.features.kanban.KanbanViewModel
import com.example.hermes.features.mobilecontrol.MobileControlScreen
import com.example.hermes.features.mobilecontrol.MobileControlViewModel
import com.example.hermes.features.workspace.WorkspaceHubScreen
import com.example.hermes.features.workspace.SpaceHubState
import com.example.hermes.features.workspace.SpaceSectionScreen
import com.example.hermes.features.workspace.agendaSummary
import com.example.hermes.features.workspace.notesSummary
import com.example.hermes.features.workspace.tasksSummary
import com.example.hermes.features.workspace.notes.NotesScreen
import com.example.hermes.features.workspace.calendar.CalendarScreen
import com.example.hermes.features.workspace.forum.ForumScreen
import com.example.hermes.features.kanban.components.KanbanTaskDetailScreen
import com.example.hermes.R
import androidx.compose.ui.res.stringResource
import com.example.hermes.features.workspace.calendar.CalendarViewModel
import com.example.hermes.features.workspace.forum.ForumViewModel
import com.example.hermes.features.workspace.notes.NotesViewModel
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.SessionBanner
import com.example.hermes.ui.components.personasOf
import com.example.hermes.ui.components.StopControlDialog

class AppViewModels(
    val chat: ChatViewModel,
    val kanban: KanbanViewModel,
    val notes: NotesViewModel,
    val calendar: CalendarViewModel,
    val forum: ForumViewModel,
    val mobileControl: MobileControlViewModel,
)

/** The signed-in app: five tabs, one back stack each, the agent sheets and dialogs above them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppScaffold(vm: AppViewModels, onDisconnect: () -> Unit) {
    // Only the tab survives recreation; each stack restarts at its root.
    var savedTab by rememberSaveable { mutableStateOf(Tab.Chat) }
    var nav by remember { mutableStateOf(TabNavigator.start(::rootOf).select(savedTab)) }
    fun go(next: TabNavigator<NavKey>) {
        nav = next
        savedTab = next.current
    }

    val control by vm.mobileControl.uiState.collectAsState()
    val chat by vm.chat.uiState.collectAsState()
    val inboxCount = inboxCounts(chat.pendingApproval, chat.memoryActions, chat.memoryValidations).total
    fun refreshInbox() {
        vm.chat.loadMemoryValidations()
        vm.chat.loadMemoryActions()
    }
    // The badge needs the counts before the tab is ever opened.
    LaunchedEffect(Unit) {
        refreshInbox()
        vm.mobileControl.refreshProfiles()
    }
    var confirmStop by remember { mutableStateOf(false) }

    val tools = rememberAgentToolsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var spaceLoaded by remember { mutableStateOf(false) }

    fun onSelect(tab: Tab) {
        if (tab == Tab.Inbox) refreshInbox()
        if (tab == Tab.Agent) vm.chat.loadWorkspaces()
        if (tab == Tab.Control) vm.mobileControl.refreshProfiles()
        if (tab == Tab.Space && !spaceLoaded) {
            vm.kanban.loadData()
            vm.notes.loadNotes()
            vm.calendar.loadCalendarData()
            vm.forum.loadData()
            spaceLoaded = true
        }
        go(nav.select(tab))
    }

    // NavDisplay handles back inside a stack; at a root other than Chat, back returns to Chat.
    BackHandler(enabled = nav.currentStack.size == 1 && nav.current != Tab.Chat) {
        nav.back()?.let(::go)
    }

    Scaffold(
        containerColor = HermesColors.Background,
        // Screens keep their own status-bar handling; only the bar's height is reserved here.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!WindowInsets.isImeVisible) {
                AppNavigationBar(current = nav.current, onSelect = ::onSelect, inboxCount = inboxCount, controlLive = control.activeSession != null)
            }
        },
    ) { padding ->
        // imePadding lifts the screens above the keyboard; without it Android pans the whole window
        // and the top bar leaves the screen.
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            // Above every tab while the agent controls the phone; it takes the status bar over.
            val session = control.activeSession
            if (session != null) {
                SessionBanner(
                    startedAtMs = session.startedAt,
                    onStop = { confirmStop = true },
                    onOpen = { if (nav.current != Tab.Control) onSelect(Tab.Control) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                )
            }
            Box(Modifier.weight(1f).then(if (session != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                NavDisplay(
                    backStack = nav.currentStack,
                    onBack = { nav.back()?.let(::go) },
                    entryProvider = entryProvider {
                        entry<ChatRoute> {
                            ChatScreen(
                                viewModel = vm.chat,
                                tools = tools,
                                snackbarHostState = snackbarHostState,
                                onOpenAgent = { onSelect(Tab.Agent) },
                            )
                        }
                        entry<InboxRoute> {
                            InboxScreen(
                                state = InboxState(
                                    approval = chat.pendingApproval,
                                    approvalContext = listOfNotNull(
                                        profileDisplayName(chat.activeProfile),
                                        chatSessionTitle(chat.sessions, chat.sessionId),
                                    ).joinToString(" · "),
                                    actions = chat.memoryActions,
                                    memory = chat.memoryValidations,
                                    memoryLoading = chat.isValidationsLoading,
                                    memoryError = chat.validationsError,
                                    hasOwnerToken = chat.hasMemoryOwnerToken,
                                    checkedAt = chat.memoryCheckedAt?.let { shortTime(Instant.ofEpochMilli(it).toString()) },
                                ),
                                onRefresh = ::refreshInbox,
                                onRespondTool = { vm.chat.respondApproval(it) },
                                onOpenConversation = { onSelect(Tab.Chat) },
                                onDecideAction = { id, approve -> vm.chat.decideMemoryAction(id, approve) },
                                onDecideMemory = { id, confirm -> vm.chat.decideMemoryValidation(id, confirm) },
                                onChangeToken = { go(nav.open(Tab.Agent, SettingsRoute)) },
                            )
                        }
                        entry<SpaceRoute> {
                        val kanban by vm.kanban.uiState.collectAsState()
                        val notes by vm.notes.uiState.collectAsState()
                        val calendar by vm.calendar.uiState.collectAsState()
                        WorkspaceHubScreen(
                            state = SpaceHubState(
                                tasks = tasksSummary(kanban.columns),
                                board = kanban.currentBoard,
                                notes = notesSummary(notes.notes),
                                agenda = agendaSummary(calendar.events),
                            ),
                            onOpenTasks = {
                                vm.kanban.loadData()
                                go(nav.push(KanbanRoute))
                            },
                            onOpenNotes = { go(nav.push(NotesRoute)) },
                            onOpenAgenda = { go(nav.push(AgendaRoute)) },
                            onOpenForum = { go(nav.push(ForumRoute)) },
                            onDictate = {
                                go(nav.push(NotesRoute))
                                vm.notes.openDictaphoneModal()
                            },
                            onNewTask = {
                                go(nav.push(KanbanRoute))
                                vm.kanban.showCreateDialog(true)
                            },
                        )
                    }
                    entry<NotesRoute> {
                        SpaceSectionScreen(stringResource(R.string.space_notes), onBack = { nav.back()?.let(::go) }) {
                            NotesScreen(viewModel = vm.notes, personas = personasOf(chat.profiles))
                        }
                    }
                    entry<AgendaRoute> {
                        SpaceSectionScreen(stringResource(R.string.space_agenda), onBack = { nav.back()?.let(::go) }) {
                            CalendarScreen(viewModel = vm.calendar, personas = personasOf(chat.profiles))
                        }
                    }
                    entry<ForumRoute> {
                        SpaceSectionScreen(stringResource(R.string.space_forum), onBack = { nav.back()?.let(::go) }) {
                            ForumScreen(viewModel = vm.forum, personas = personasOf(chat.profiles))
                        }
                    }
                    entry<ControlRoute> {
                        MobileControlScreen(viewModel = vm.mobileControl, onOpenRelaySettings = { go(nav.open(Tab.Agent, RelaySettingsRoute)) })
                    }
                        entry<AgentRoute> {
                        val active = chat.profiles.firstOrNull { it.name == chat.activeProfile } ?: ProfileInfo(name = chat.activeProfile)
                        AgentScreen(
                            state = AgentState(
                                profile = active,
                                activeWorkspace = chat.workspaces.firstOrNull { it.path == chat.activeWorkspace }?.name
                                    ?: chat.activeWorkspace,
                                guardPending = chat.memoryValidations.size + chat.memoryActions.size,
                                isRestartingGateway = chat.isRestartingGateway,
                            ),
                            onChangeProfile = { tools.profilesSheet = true },
                            onOpenSkills = {
                                vm.chat.loadSkills()
                                go(nav.push(SkillsRoute))
                            },
                            onOpenMemory = {
                                vm.chat.loadMemory()
                                go(nav.push(MemoryRoute))
                            },
                            onOpenWorkspaces = {
                                vm.chat.loadWorkspaces()
                                go(nav.push(WorkspacesRoute))
                            },
                            onOpenEnv = {
                                vm.chat.loadProviders()
                                go(nav.push(EnvRoute))
                            },
                            onOpenGuard = {
                                refreshInbox()
                                vm.chat.loadMemoryProfiles()
                                vm.chat.loadMemoryMandates()
                                go(nav.push(GuardRoute))
                            },
                            onRestartGateway = { vm.chat.restartGateway() },
                            onOpenSettings = {
                                vm.chat.loadOpenbaoHealth()
                                go(nav.push(SettingsRoute))
                            },
                        )
                    }
                    entry<SkillsRoute> {
                        SkillsScreen(
                            profileName = profileDisplayName(chat.activeProfile),
                            skills = chat.skills,
                            isLoading = chat.isSkillsLoading,
                            onOpenSkill = { name ->
                                vm.chat.loadSkillDetail(name)
                                go(nav.push(SkillDetailRoute(name)))
                            },
                            onBack = { nav.back()?.let(::go) },
                        )
                    }
                    entry<SkillDetailRoute> { route ->
                        val back = {
                            vm.chat.clearSkillDetail()
                            nav.back()?.let(::go)
                        }
                        SkillDetailScreen(
                            name = route.name,
                            detail = chat.selectedSkillDetail,
                            isLoading = chat.isSkillsLoading,
                            error = chat.skillDetailError,
                            onRetry = { vm.chat.loadSkillDetail(route.name) },
                            onDelete = {
                                vm.chat.deleteSkill(route.name)
                                back()
                            },
                            onBack = { back() },
                        )
                    }
                    entry<MemoryRoute> {
                        MemoryScreen(
                            profileName = profileDisplayName(chat.activeProfile),
                            memory = chat.memoryData,
                            isLoading = chat.isMemoryLoading,
                            onSave = { section, content -> vm.chat.saveMemory(section.apiName, content) },
                            onBack = { nav.back()?.let(::go) },
                        )
                    }
                    entry<WorkspacesRoute> {
                        WorkspacesScreen(
                            profileName = profileDisplayName(chat.activeProfile),
                            workspaces = chat.workspaces,
                            activePath = chat.activeWorkspace,
                            isLoading = chat.isWorkspacesLoading,
                            onSelect = { vm.chat.selectWorkspace(it) },
                            onAdd = { path, name -> vm.chat.addWorkspace(path, name) },
                            onRemove = { vm.chat.removeWorkspace(it) },
                            onBack = { nav.back()?.let(::go) },
                        )
                    }
                    entry<RelaySettingsRoute> {
                        // Pairing results arrive as notices on the control state.
                        LaunchedEffect(control.error, control.successMessage) {
                            (control.error ?: control.successMessage)?.let {
                                snackbarHostState.showSnackbar(it)
                                vm.mobileControl.dismissMessage()
                            }
                        }
                        RelaySettingsScreen(
                            relayUrl = control.relayUrl,
                            deviceId = control.deviceId,
                            isPaired = control.isPaired,
                            isConnected = control.isRelayConnected,
                            onSaveUrl = { vm.mobileControl.updateRelayUrl(it) },
                            onPair = { vm.mobileControl.pairDevice(it) },
                            onUnpair = { vm.mobileControl.unpairDevice() },
                            onBack = { nav.back()?.let(::go) },
                        )
                    }
                    entry<GuardRoute> {
                        GuardScreen(
                            state = GuardState(
                                memory = chat.memoryValidations,
                                actions = chat.memoryActions,
                                pendingLoading = chat.isValidationsLoading,
                                pendingError = chat.validationsError,
                                profiles = chat.memoryProfiles,
                                profilesLoading = chat.isProfilesLoading,
                                profilesError = chat.profilesError,
                                mandates = chat.memoryMandates,
                                mandatesLoading = chat.isMandatesLoading,
                                mandatesError = chat.mandatesError,
                                hasOwnerToken = chat.hasMemoryOwnerToken,
                            ),
                            actions = GuardActions(
                                onRefresh = { t ->
                                    when (t) {
                                        GuardTab.Pending, GuardTab.Actions -> refreshInbox()
                                        GuardTab.Profiles -> vm.chat.loadMemoryProfiles()
                                        GuardTab.Mandates -> vm.chat.loadMemoryMandates()
                                    }
                                },
                                onDecideMemory = { id, confirm -> vm.chat.decideMemoryValidation(id, confirm) },
                                onDecideAction = { id, approve -> vm.chat.decideMemoryAction(id, approve) },
                                onSaveGrants = { name, read, write -> vm.chat.setMemoryProfileGrants(name, read, write) },
                                onRevokeProfile = { vm.chat.revokeMemoryProfile(it) },
                                onNewProfile = { go(nav.push(NewMemoryProfileRoute)) },
                                onRevokeMandate = { vm.chat.revokeMemoryMandate(it) },
                                onNewMandate = { go(nav.push(NewMandateRoute)) },
                                onChangeToken = { go(nav.push(SettingsRoute)) },
                                onBack = { nav.back()?.let(::go) },
                            ),
                        )
                    }
                    entry<NewMemoryProfileRoute> {
                        NewMemoryProfileScreen(
                            isLoading = chat.isProfilesLoading,
                            error = chat.profilesError,
                            created = chat.createdProfile,
                            onCreate = { name, read, write -> vm.chat.createMemoryProfile(name, read, write) },
                            onTokenSeen = {
                                vm.chat.dismissCreatedProfileToken()
                                nav.back()?.let(::go)
                            },
                            onBack = {
                                vm.chat.dismissCreatedProfileToken()
                                nav.back()?.let(::go)
                            },
                        )
                    }
                    entry<NewMandateRoute> {
                        NewMandateScreen(
                            activeProfile = chat.activeProfile,
                            profileName = profileDisplayName(chat.activeProfile),
                            memoryProfiles = chat.memoryProfiles,
                            isLoading = chat.isMandatesLoading,
                            error = chat.mandatesError,
                            onCreate = { profile, label, hosts, folders, expiresAt ->
                                vm.chat.createMemoryMandate(profile, label, hosts, folders, expiresAt) { nav.back()?.let(::go) }
                            },
                            onBack = { nav.back()?.let(::go) },
                        )
                    }
                    entry<EnvRoute> {
                        ProfileEnvScreen(
                            activeProfile = chat.activeProfile,
                            envEntries = chat.envEntries,
                            providers = chat.providers,
                            isLoading = chat.isProvidersLoading || chat.isEnvLoading,
                            isEnvFallback = chat.isEnvFallback,
                            onSaveEnvVar = { key, value, onFinished ->
                                vm.chat.setProfileEnvVar(key, value) { success, errorMsg ->
                                    onFinished(success, errorMsg)
                                    if (success) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Variable $key enregistrée dans le .env du profil ${chat.activeProfile}")
                                        }
                                    }
                                }
                            },
                            onDeleteEnvVar = { key, onFinished ->
                                vm.chat.deleteProfileEnvVar(key) { success, errorMsg ->
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
                                vm.chat.setProviderKey(providerId, apiKey) { success, errorMsg ->
                                    onFinished(success, errorMsg)
                                    if (success) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Clé $providerId enregistrée dans le .env du profil ${chat.activeProfile}")
                                        }
                                    }
                                }
                            },
                            onDeleteProviderKey = { providerId, onFinished ->
                                vm.chat.deleteProviderKey(providerId) { success, errorMsg ->
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
                                vm.chat.loadProfileEnv()
                                vm.chat.loadProviders()
                            },
                            onOpenOpenbao = {
                                vm.chat.loadOpenbaoSecrets()
                                go(nav.push(OpenBaoRoute))
                            },
                            onDismiss = { nav.back()?.let(::go) }
                        )
                    }
                    entry<OpenBaoRoute> {
                        OpenBaoVaultScreen(
                            activeProfile = chat.activeProfile,
                            openbaoUrl = chat.openbaoUrl,
                            openbaoToken = chat.openbaoToken,
                            openbaoMount = chat.openbaoMount,
                            health = chat.openbaoHealth,
                            secrets = chat.openbaoSecrets,
                            isLoading = chat.isOpenbaoLoading,
                            errorMessage = chat.openbaoError,
                            onSaveConfig = { url, token, mount, onFinished ->
                                vm.chat.saveOpenbaoConfig(url, token, mount, onFinished)
                            },
                            onSaveSecret = { key, value, onFinished ->
                                vm.chat.saveOpenbaoSecret(key, value, chat.activeProfile) { success, err ->
                                    onFinished(success, err)
                                    if (success) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Secret $key enregistré dans OpenBao. Redémarrez le conteneur ${chat.activeProfile} pour appliquer.")
                                        }
                                    }
                                }
                            },
                            onDeleteSecret = { key, onFinished ->
                                vm.chat.deleteOpenbaoSecret(key, chat.activeProfile) { success, err ->
                                    onFinished(success, err)
                                    if (success) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Secret $key supprimé d'OpenBao. Redémarrez le conteneur ${chat.activeProfile} pour appliquer.")
                                        }
                                    }
                                }
                            },
                            onRefresh = {
                                vm.chat.loadOpenbaoHealth()
                                vm.chat.loadOpenbaoSecrets()
                            },
                            onRestartGateway = {
                                vm.chat.restartGateway()
                            },
                            isRestartingGateway = chat.isRestartingGateway,
                            onDismiss = { nav.back()?.let(::go) }
                        )
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            serverUrl = chat.serverUrl,
                            sessionId = chat.sessionId,
                            relayPaired = control.isPaired,
                            relayConnected = control.isRelayConnected,
                            openBaoStatus = openBaoStatus(chat.openbaoUrl, chat.openbaoHealth),
                            hasOwnerToken = chat.hasMemoryOwnerToken,
                            onBack = { nav.back()?.let(::go) },
                            onOpenRelay = { go(nav.push(RelaySettingsRoute)) },
                            onSaveOwnerToken = { vm.chat.saveMemoryOwnerToken(it) },
                            onOpenOpenBao = {
                                vm.chat.loadOpenbaoSecrets()
                                go(nav.push(OpenBaoRoute))
                            },
                            onDisconnect = onDisconnect,
                        )
                    }
                        entry<KanbanRoute> {
                        KanbanScreen(
                            viewModel = vm.kanban,
                            onNavigateBack = { nav.back()?.let(::go) },
                            onOpenTask = { task ->
                                vm.kanban.selectTask(task)
                                go(nav.push(KanbanTaskRoute))
                            },
                        )
                    }
                    entry<KanbanTaskRoute> {
                        val kanban by vm.kanban.uiState.collectAsState()
                        val back = {
                            vm.kanban.selectTask(null)
                            nav.back()?.let(::go)
                        }
                        kanban.selectedTask?.let { task ->
                            KanbanTaskDetailScreen(
                                task = task,
                                boardName = kanban.currentBoard,
                                onDismiss = { back() },
                                onSave = { title, body, status, priority, assignee ->
                                    vm.kanban.updateTask(task.id, title, body, status, priority, assignee)
                                },
                                onBlock = { reason -> vm.kanban.blockTask(task.id, reason) },
                                onUnblock = { vm.kanban.unblockTask(task.id) },
                                onArchive = { vm.kanban.archiveTask(task.id) },
                            )
                        }
                    }
                    },
                )
            }
        }
    }

    if (confirmStop) {
        StopControlDialog(
            onConfirm = {
                confirmStop = false
                vm.mobileControl.stopSession()
            },
            onDismiss = { confirmStop = false },
        )
    }

    AgentToolsHost(
        viewModel = vm.chat,
        tools = tools,
        snackbarHostState = snackbarHostState,
    )
}
