package com.example.hermes

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.hermes.core.audio.AudioPlayerManager
import com.example.hermes.core.audio.SpeechRecognitionManager
import com.example.hermes.features.auth.AuthScreen
import com.example.hermes.features.auth.AuthViewModel
import com.example.hermes.features.chat.ChatScreen
import com.example.hermes.features.chat.ChatViewModel
import com.example.hermes.features.kanban.KanbanScreen
import com.example.hermes.features.kanban.KanbanViewModel
import com.example.hermes.features.workspace.WorkspaceHubScreen
import com.example.hermes.features.workspace.calendar.CalendarViewModel
import com.example.hermes.features.workspace.forum.ForumViewModel
import com.example.hermes.features.mobilecontrol.MobileControlScreen
import com.example.hermes.features.mobilecontrol.MobileControlViewModel
import com.example.hermes.features.workspace.notes.NotesViewModel

@Composable
fun MainNavigation() {
    val appContext = LocalContext.current.applicationContext
    val container = (appContext as HermesApp).container

    // ViewModels are owned by the activity's ViewModelStore: they survive rotation and other
    // recreations. Nothing here is built with remember{}, which would be lost on recreation.
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory {
        initializer { AuthViewModel(container.repository) }
    })
    val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatViewModel(container.repository) }
    })
    val kanbanViewModel: KanbanViewModel = viewModel(factory = viewModelFactory {
        initializer { KanbanViewModel(container.repository) }
    })
    val notesViewModel: NotesViewModel = viewModel(factory = viewModelFactory {
        initializer {
            // The ViewModel destroys these in onCleared(), so they are created with it.
            NotesViewModel(container.notesRepository, SpeechRecognitionManager(appContext), AudioPlayerManager(appContext))
        }
    })
    val calendarViewModel: CalendarViewModel = viewModel(factory = viewModelFactory {
        initializer { CalendarViewModel(container.calendarRepository) }
    })
    val forumViewModel: ForumViewModel = viewModel(factory = viewModelFactory {
        initializer { ForumViewModel(container.forumRepository) }
    })
    val mobileControlViewModel: MobileControlViewModel = viewModel(factory = viewModelFactory {
        initializer {
            MobileControlViewModel(appContext, container.mobileControlManager, container.repository, container.preferences)
        }
    })

    val backStack = rememberNavBackStack(AuthRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<AuthRoute> {
                AuthScreen(
                    viewModel = authViewModel,
                    onNavigateToChat = {
                        backStack.add(ChatRoute)
                        chatViewModel.loadInitialData()
                    }
                )
            }
            entry<ChatRoute> {
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigateToSettings = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToKanban = {
                        backStack.add(KanbanRoute)
                        kanbanViewModel.loadData()
                    },
                    onNavigateToWorkspace = {
                        backStack.add(WorkspaceRoute)
                        notesViewModel.loadNotes()
                        calendarViewModel.loadCalendarData()
                        forumViewModel.loadData()
                    },
                    onNavigateToMobileControl = {
                        backStack.add(MobileControlRoute)
                    }
                )
            }
            entry<KanbanRoute> {
                KanbanScreen(
                    viewModel = kanbanViewModel,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<WorkspaceRoute> {
                WorkspaceHubScreen(
                    notesViewModel = notesViewModel,
                    calendarViewModel = calendarViewModel,
                    forumViewModel = forumViewModel,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<MobileControlRoute> {
                MobileControlScreen(
                    viewModel = mobileControlViewModel,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}

