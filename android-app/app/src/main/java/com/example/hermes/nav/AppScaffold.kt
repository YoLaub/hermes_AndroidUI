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
import com.example.hermes.features.settings.SettingsScreen
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
    LaunchedEffect(Unit) { refreshInbox() }
    var confirmStop by remember { mutableStateOf(false) }

    val tools = rememberAgentToolsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var spaceLoaded by remember { mutableStateOf(false) }

    fun onSelect(tab: Tab) {
        if (tab == Tab.Inbox) refreshInbox()
        if (tab == Tab.Agent) vm.chat.loadWorkspaces()
        if (tab == Tab.Space && !spaceLoaded) {
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
                                // The token field moves to Réglages; until then it lives in this dialog.
                                onChangeToken = { tools.validationsDialog = true },
                            )
                        }
                        entry<SpaceRoute> {
                            WorkspaceHubScreen(
                                notesViewModel = vm.notes,
                                calendarViewModel = vm.calendar,
                                forumViewModel = vm.forum,
                                personas = personasOf(chat.profiles),
                                onOpenTasks = {
                                    vm.kanban.loadData()
                                    go(nav.push(KanbanRoute))
                                },
                            )
                        }
                        entry<ControlRoute> { MobileControlScreen(viewModel = vm.mobileControl) }
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
                                tools.skillsDialog = true
                            },
                            onOpenMemory = {
                                vm.chat.loadMemory()
                                tools.memoryDialog = true
                            },
                            onOpenWorkspaces = {
                                vm.chat.loadWorkspaces()
                                tools.workspaceDialog = true
                            },
                            onOpenEnv = {
                                vm.chat.loadProviders()
                                tools.profileEnvDialog = true
                            },
                            onOpenValidations = {
                                refreshInbox()
                                tools.validationsDialog = true
                            },
                            onOpenMemoryProfiles = {
                                vm.chat.loadMemoryProfiles()
                                tools.memoryProfilesDialog = true
                            },
                            onOpenMandates = {
                                vm.chat.loadMemoryMandates()
                                tools.mandatesDialog = true
                            },
                            onRestartGateway = { vm.chat.restartGateway() },
                            onOpenSettings = { go(nav.push(SettingsRoute)) },
                        )
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            serverUrl = chat.serverUrl,
                            sessionId = chat.sessionId,
                            onBack = { nav.back()?.let(::go) },
                            onOpenOpenBao = {
                                vm.chat.loadOpenbaoSecrets()
                                tools.openBaoDialog = true
                            },
                            onDisconnect = onDisconnect,
                        )
                    }
                        entry<KanbanRoute> {
                            KanbanScreen(viewModel = vm.kanban, onNavigateBack = { nav.back()?.let(::go) })
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
