package com.example.hermes.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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

    val tools = rememberAgentToolsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var spaceLoaded by remember { mutableStateOf(false) }

    fun onSelect(tab: Tab) {
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
                AppNavigationBar(current = nav.current, onSelect = ::onSelect)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
            NavDisplay(
                backStack = nav.currentStack,
                onBack = { nav.back()?.let(::go) },
                entryProvider = entryProvider {
                    entry<ChatRoute> {
                        ChatScreen(
                            viewModel = vm.chat,
                            tools = tools,
                            snackbarHostState = snackbarHostState,
                            onNavigateToKanban = {
                                vm.kanban.loadData()
                                onSelect(Tab.Space)
                                go(nav.open(Tab.Space, KanbanRoute))
                            },
                            onNavigateToWorkspace = { onSelect(Tab.Space) },
                            onNavigateToMobileControl = { onSelect(Tab.Control) },
                        )
                    }
                    entry<InboxRoute> { ProvisionalInboxScreen(vm.chat, tools) }
                    entry<SpaceRoute> {
                        WorkspaceHubScreen(
                            notesViewModel = vm.notes,
                            calendarViewModel = vm.calendar,
                            forumViewModel = vm.forum,
                        )
                    }
                    entry<ControlRoute> { MobileControlScreen(viewModel = vm.mobileControl) }
                    entry<AgentRoute> { ProvisionalAgentScreen(tools) }
                    entry<KanbanRoute> {
                        KanbanScreen(viewModel = vm.kanban, onNavigateBack = { nav.back()?.let(::go) })
                    }
                },
            )
        }
    }

    AgentToolsHost(
        viewModel = vm.chat,
        tools = tools,
        snackbarHostState = snackbarHostState,
        onDisconnect = onDisconnect,
    )
}
