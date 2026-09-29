package com.example.hermes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.hermes.core.data.HermesPreferences
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.features.auth.AuthScreen
import com.example.hermes.features.auth.AuthViewModel
import com.example.hermes.features.chat.ChatScreen
import com.example.hermes.features.chat.ChatViewModel

@Composable
fun MainNavigation() {
    val context = LocalContext.current.applicationContext
    val preferences = remember { HermesPreferences(context) }
    val repository = remember { HermesRepository(preferences) }

    val authViewModel = remember { AuthViewModel(repository) }
    val chatViewModel = remember { ChatViewModel(repository) }

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
                    }
                )
            }
        }
    )
}
