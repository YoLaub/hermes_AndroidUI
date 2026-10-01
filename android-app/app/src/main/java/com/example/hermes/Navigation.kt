package com.example.hermes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.hermes.core.audio.AudioPlayerManager
import com.example.hermes.core.audio.SpeechRecognitionManager
import com.example.hermes.core.data.CalendarRepository
import com.example.hermes.core.data.ForumRepository
import com.example.hermes.core.data.HermesPreferences
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.core.data.NotesRepository
import com.example.hermes.features.auth.AuthScreen
import com.example.hermes.features.auth.AuthViewModel
import com.example.hermes.features.chat.ChatScreen
import com.example.hermes.features.chat.ChatViewModel
import com.example.hermes.features.kanban.KanbanScreen
import com.example.hermes.features.kanban.KanbanViewModel
import com.example.hermes.features.workspace.WorkspaceHubScreen
import com.example.hermes.features.workspace.calendar.CalendarViewModel
import com.example.hermes.features.workspace.forum.ForumViewModel
import com.example.hermes.core.mobilecontrol.MobileControlManager
import com.example.hermes.features.mobilecontrol.MobileControlScreen
import com.example.hermes.features.mobilecontrol.MobileControlViewModel
import com.example.hermes.features.workspace.notes.NotesViewModel

@Composable
fun MainNavigation() {
    val context = LocalContext.current.applicationContext
    val preferences = remember { HermesPreferences(context) }
    val repository = remember { HermesRepository(preferences) }
    val notesRepository = remember { NotesRepository(context) }
    val calendarRepository = remember { CalendarRepository(context) }
    val forumRepository = remember { ForumRepository(context) }
    val speechManager = remember { SpeechRecognitionManager(context) }
    val audioPlayer = remember { AudioPlayerManager(context) }
    val mobileControlManager = remember { MobileControlManager(context, preferences) }

    val authViewModel = remember { AuthViewModel(repository) }
    val chatViewModel = remember { ChatViewModel(repository) }
    val kanbanViewModel = remember { KanbanViewModel(repository) }
    val notesViewModel = remember { NotesViewModel(notesRepository, speechManager, audioPlayer) }
    val calendarViewModel = remember { CalendarViewModel(calendarRepository) }
    val forumViewModel = remember { ForumViewModel(forumRepository) }
    val mobileControlViewModel = remember { MobileControlViewModel(context, mobileControlManager, repository, preferences) }

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

