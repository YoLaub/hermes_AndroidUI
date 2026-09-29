package com.example.hermes.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.core.model.HealthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Checking : ConnectionStatus()
    data class Success(val health: HealthResponse, val authRequired: Boolean) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

data class AuthUiState(
    val serverUrl: String = "http://10.0.2.2:8000",
    val password: String = "",
    val isConnecting: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val isConnected: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val repository: HermesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl = repository.serverUrl.first()
            val savedPassword = repository.password.first()
            _uiState.update { 
                it.copy(
                    serverUrl = savedUrl,
                    password = savedPassword ?: ""
                ) 
            }
            testConnection()
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, connectionStatus = ConnectionStatus.Idle, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun testConnection() {
        val url = _uiState.value.serverUrl.trim().removeSuffix("/")
        if (url.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionStatus = ConnectionStatus.Checking, errorMessage = null) }
            repository.setServerUrl(url)

            val healthResult = repository.checkHealth()
            if (healthResult.isSuccess) {
                val health = healthResult.getOrThrow()
                val authResult = repository.checkAuthStatus()
                val authRequired = authResult.getOrNull()?.authEnabled ?: false
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionStatus = ConnectionStatus.Success(health, authRequired)
                    )
                }
            } else {
                val errorMsg = healthResult.exceptionOrNull()?.localizedMessage ?: "Failed to connect to Hermes instance"
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionStatus = ConnectionStatus.Error(errorMsg)
                    )
                }
            }
        }
    }

    fun connect(onSuccess: () -> Unit) {
        val state = _uiState.value
        val url = state.serverUrl.trim().removeSuffix("/")

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            repository.setServerUrl(url)

            // Check if auth is required
            val authStatus = repository.checkAuthStatus().getOrNull()
            if (authStatus?.authEnabled == true && !authStatus.loggedIn) {
                // Perform login
                val loginResult = repository.login(state.password)
                if (loginResult.isSuccess && loginResult.getOrNull()?.ok == true) {
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                    onSuccess()
                } else {
                    val err = loginResult.getOrNull()?.error ?: loginResult.exceptionOrNull()?.localizedMessage ?: "Authentication failed"
                    _uiState.update { it.copy(isConnecting = false, errorMessage = err) }
                }
            } else {
                // No auth required or already logged in
                if (state.password.isNotBlank()) {
                    repository.setPassword(state.password)
                }
                _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                onSuccess()
            }
        }
    }
}
