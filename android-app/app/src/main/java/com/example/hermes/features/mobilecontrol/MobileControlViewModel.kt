package com.example.hermes.features.mobilecontrol

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.accessibility.HermesAccessibilityService
import com.example.hermes.core.data.HermesPreferences
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.core.mobilecontrol.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class MobileControlUiState(
    val isRelayConnected: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val relayUrl: String = "",
    val deviceId: String = "",
    val isPaired: Boolean = false,
    val activeSession: MobileControlSession? = null,
    val allowedApps: List<AllowedApp> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val availableProfiles: List<String> = emptyList(),
    val activeProfile: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

class MobileControlViewModel(
    private val context: Context,
    private val manager: MobileControlManager,
    private val repository: HermesRepository,
    private val preferences: HermesPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MobileControlUiState(
            relayUrl = "https://mobile-relay-hermes.john-world.store",
            deviceId = "dev_" + UUID.randomUUID().toString().take(8)
        )
    )
    val uiState: StateFlow<MobileControlUiState> = _uiState.asStateFlow()

    init {
        // Collect accessibility state
        viewModelScope.launch {
            HermesAccessibilityService.isServiceActive.collect { active ->
                _uiState.update { it.copy(isAccessibilityEnabled = active) }
            }
        }

        // Collect WS connection state
        viewModelScope.launch {
            manager.wsClient.isConnected.collect { connected ->
                _uiState.update { it.copy(isRelayConnected = connected) }
            }
        }

        // Collect Active Session
        viewModelScope.launch {
            manager.activeSession.collect { session ->
                _uiState.update { it.copy(activeSession = session) }
            }
        }

        // Collect Allowed Apps
        viewModelScope.launch {
            manager.allowedApps.collect { apps ->
                _uiState.update { it.copy(allowedApps = apps) }
            }
        }

        // Collect Audit Logs
        viewModelScope.launch {
            manager.auditLogs.collect { logs ->
                _uiState.update { it.copy(auditLogs = logs) }
            }
        }

        // Collect mobile relay URL preferences
        viewModelScope.launch {
            preferences.mobileRelayUrl.collect { url ->
                if (!url.isNullOrBlank()) {
                    _uiState.update { it.copy(relayUrl = url) }
                } else {
                    _uiState.update { it.copy(relayUrl = "https://mobile-relay-hermes.john-world.store") }
                }
            }
        }

        viewModelScope.launch {
            preferences.mobileDeviceId.collect { id ->
                if (!id.isNullOrBlank()) {
                    _uiState.update { it.copy(deviceId = id) }
                } else {
                    val newId = "dev_" + UUID.randomUUID().toString().take(8)
                    preferences.setMobileDeviceId(newId)
                    _uiState.update { it.copy(deviceId = newId) }
                }
            }
        }

        viewModelScope.launch {
            preferences.mobileDeviceToken.collect { token ->
                val paired = !token.isNullOrBlank()
                _uiState.update { it.copy(isPaired = paired) }
                if (paired) {
                    manager.wsClient.connect(
                        relayUrl = _uiState.value.relayUrl,
                        deviceId = _uiState.value.deviceId,
                        deviceToken = token
                    )
                }
            }
        }

        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            val res = repository.getProfiles()
            if (res.isSuccess) {
                val data = res.getOrThrow()
                // Supprimer les profils de démonstration et dédupliquer par identifiant technique
                val johnProfiles = data.profiles
                    .map { it.name.trim().lowercase() }
                    .filter { it == "john" }
                    .distinct()

                if (johnProfiles.contains("john")) {
                    _uiState.update {
                        it.copy(
                            availableProfiles = listOf("john"),
                            activeProfile = "john",
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            availableProfiles = emptyList(),
                            activeProfile = "",
                            error = "Le profil John ('john') est introuvable sur le serveur Hermes. Le démarrage du contrôle mobile est désactivé."
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        availableProfiles = emptyList(),
                        activeProfile = "",
                        error = "Impossible de récupérer les profils Hermes. Le démarrage du contrôle mobile est désactivé."
                    )
                }
            }
        }
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun pairDevice(pairingCode: String) {
        val code = pairingCode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            val token = "tok_" + UUID.randomUUID().toString()
            preferences.setMobileDeviceToken(token)
            _uiState.update {
                it.copy(
                    isPaired = true,
                    successMessage = "Appareil appairé avec succès."
                )
            }
            manager.wsClient.connect(
                relayUrl = _uiState.value.relayUrl,
                deviceId = _uiState.value.deviceId,
                deviceToken = token
            )
        }
    }

    fun unpairDevice() {
        viewModelScope.launch {
            preferences.setMobileDeviceToken(null)
            manager.wsClient.disconnect()
            _uiState.update { it.copy(isPaired = false, successMessage = "Appareil dissocié.") }
        }
    }

    fun updateRelayUrl(newUrl: String) {
        val url = newUrl.trim()
        _uiState.update { it.copy(relayUrl = url) }
        viewModelScope.launch {
            preferences.setMobileRelayUrl(url)
            preferences.mobileDeviceToken.firstOrNull()?.let { token ->
                if (token.isNotBlank()) {
                    manager.wsClient.connect(url, _uiState.value.deviceId, token)
                }
            }
        }
    }

    fun startSession(
        targetPackage: String,
        targetAppName: String,
        profile: String,
        mode: MobileControlMode,
        durationMinutes: Int
    ) {
        if (!_uiState.value.availableProfiles.contains("john")) {
            _uiState.update {
                it.copy(error = "Le profil John ('john') n'est pas disponible. Le démarrage du contrôle mobile est désactivé.")
            }
            return
        }

        val res = manager.startSession(
            targetPackage = targetPackage,
            targetAppName = targetAppName,
            allowedProfile = "john",
            mode = mode,
            durationSeconds = durationMinutes * 60
        )
        if (res.isFailure) {
            _uiState.update { it.copy(error = res.exceptionOrNull()?.localizedMessage ?: "Erreur de démarrage") }
        } else {
            _uiState.update { it.copy(error = null, successMessage = "Session démarrée pour $targetAppName avec le profil John.") }
        }
    }

    fun stopSession() {
        manager.stopSession("user_cancelled_from_ui")
        _uiState.update { it.copy(successMessage = "Session de contrôle arrêtée.") }
    }

    fun toggleApp(packageName: String) {
        manager.toggleAllowedApp(packageName)
    }

    fun addAllowedApp(packageName: String, appName: String) {
        manager.addAllowedApp(packageName, appName)
    }

    fun clearAuditLogs() {
        manager.clearAuditLogs()
    }

    fun dismissMessage() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
