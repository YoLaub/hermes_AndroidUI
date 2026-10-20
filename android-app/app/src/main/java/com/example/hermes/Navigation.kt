package com.example.hermes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hermes.core.audio.AudioPlayerManager
import com.example.hermes.core.audio.SpeechRecognitionManager
import com.example.hermes.features.auth.AuthScreen
import com.example.hermes.features.auth.AuthViewModel
import com.example.hermes.features.chat.ChatViewModel
import com.example.hermes.features.kanban.KanbanViewModel
import com.example.hermes.features.mobilecontrol.MobileControlViewModel
import com.example.hermes.core.data.FileNoteAudioIndex
import com.example.hermes.core.data.NotesRepository
import com.example.hermes.core.data.SessionWorkspaceFiles
import com.example.hermes.features.workspace.calendar.CalendarViewModel
import com.example.hermes.features.workspace.calendar.DevicePhoneCalendar
import com.example.hermes.features.workspace.forum.ForumViewModel
import com.example.hermes.features.workspace.notes.NotesViewModel
import com.example.hermes.nav.AppScaffold
import com.example.hermes.nav.AppViewModels

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
            // Notes are files of the open session's workspace, or of the profile's latest session.
            val files = SessionWorkspaceFiles(container.repository) {
                chatViewModel.uiState.value.let { it.sessionId ?: it.sessions.firstOrNull()?.sessionId }
            }
            NotesViewModel(
                NotesRepository(files, FileNoteAudioIndex(appContext)),
                SpeechRecognitionManager(appContext),
                AudioPlayerManager(appContext),
            )
        }
    })
    val calendarViewModel: CalendarViewModel = viewModel(factory = viewModelFactory {
        initializer { CalendarViewModel(DevicePhoneCalendar(appContext), loadRuns = { container.repository.getCrons().map { it.jobs } }) }
    })
    val forumViewModel: ForumViewModel = viewModel(factory = viewModelFactory {
        initializer { ForumViewModel(container.forumRepository) }
    })
    val mobileControlViewModel: MobileControlViewModel = viewModel(factory = viewModelFactory {
        initializer {
            MobileControlViewModel(appContext, container.mobileControlManager, container.repository, container.preferences)
        }
    })

    // Connexion stays outside the tabs until an instance is validated.
    var connected by rememberSaveable { mutableStateOf(false) }

    if (!connected) {
        AuthScreen(
            viewModel = authViewModel,
            onNavigateToChat = {
                connected = true
                chatViewModel.loadInitialData()
            }
        )
    } else {
        AppScaffold(
            vm = AppViewModels(chatViewModel, kanbanViewModel, notesViewModel, calendarViewModel, forumViewModel, mobileControlViewModel),
            avatarStore = container.avatarStore,
            onDisconnect = { connected = false },
        )
    }
}
