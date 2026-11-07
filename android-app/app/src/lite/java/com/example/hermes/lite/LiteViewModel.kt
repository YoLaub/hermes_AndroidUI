package com.example.hermes.lite

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.HermesApp
import com.example.hermes.core.mobilecontrol.DEFAULT_RELAY_URL
import com.example.hermes.core.mobilecontrol.OkHttpPairingTransport
import com.example.hermes.core.mobilecontrol.PairingClient
import com.example.hermes.core.mobilecontrol.PairingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class LiteUiState(
    val relayUrl: String = DEFAULT_RELAY_URL,
    val paired: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

class LiteViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as HermesApp).container
    private val preferences = container.preferences
    private val manager = container.mobileControlManager
    private val link = container.agentPhoneLink
    private val pairing = PairingClient(OkHttpPairingTransport())

    private val _ui = MutableStateFlow(LiteUiState())
    val ui: StateFlow<LiteUiState> = _ui.asStateFlow()

    val authenticated = manager.wsClient.isAuthenticated
    val activeSession = manager.activeSession
    val stopped = link.stopped

    init {
        viewModelScope.launch {
            preferences.mobileRelayUrl.collect { url ->
                _ui.update { it.copy(relayUrl = url?.takeIf { u -> u.isNotBlank() } ?: DEFAULT_RELAY_URL) }
            }
        }
        viewModelScope.launch {
            preferences.mobileDeviceToken.collect { token -> _ui.update { it.copy(paired = !token.isNullOrBlank()) } }
        }
    }

    fun onRelayUrlChanged(url: String) = _ui.update { it.copy(relayUrl = url) }

    fun pair(code: String) {
        val clean = code.trim()
        if (clean.isBlank() || _ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            val url = _ui.value.relayUrl.trim()
            var deviceId = preferences.mobileDeviceId.firstOrNull()
            if (deviceId.isNullOrBlank()) {
                deviceId = "dev_" + UUID.randomUUID().toString().take(8)
                preferences.setMobileDeviceId(deviceId)
            }
            val result = pairing.pair(
                relayUrl = url,
                code = clean,
                deviceId = deviceId,
                deviceName = Build.MODEL ?: "Android Phone",
                currentDeviceToken = preferences.mobileDeviceToken.firstOrNull(),
                deviceKind = "agent_phone"
            )
            when (result) {
                is PairingResult.Success -> {
                    preferences.setMobileRelayUrl(url)
                    // Storing the token is what makes the link open the authenticated socket.
                    val stored = preferences.setMobileDeviceToken(result.deviceToken)
                    link.resume()
                    _ui.update { it.copy(busy = false, error = if (stored) null else "Le jeton n'a pas pu être stocké de façon sécurisée. Réessayez.") }
                }
                is PairingResult.Failure -> _ui.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun unpair() {
        viewModelScope.launch { preferences.setMobileDeviceToken(null) }
    }

    fun stop() = link.stop()
    fun resume() = link.resume()
}
