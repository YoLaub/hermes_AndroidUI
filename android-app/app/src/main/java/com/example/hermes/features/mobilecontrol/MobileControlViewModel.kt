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
    /** Start requested, relay has not confirmed yet (never displayed as active). */
    val pendingSession: MobileControlSession? = null,
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
    private val preferences: HermesPreferences,
    private val pairingClient: PairingClient = PairingClient(OkHttpPairingTransport())
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

        // "Connected" means authenticated by the relay, not merely a socket that is open.
        viewModelScope.launch {
            manager.wsClient.isAuthenticated.collect { authenticated ->
                _uiState.update { it.copy(isRelayConnected = authenticated) }
            }
        }

        viewModelScope.launch {
            manager.wsClient.connectionError.collect { err ->
                if (err != null) _uiState.update { it.copy(error = err) }
            }
        }

        viewModelScope.launch {
            manager.pendingSession.collect { pending ->
                _uiState.update { it.copy(pendingSession = pending) }
            }
        }

        // Relay answers (confirmed / refused / lost on reconnection) shown to the user.
        viewModelScope.launch {
            manager.notices.collect { n ->
                _uiState.update {
                    if (n.isError) it.copy(error = n.text, successMessage = null)
                    else it.copy(error = null, successMessage = n.text)
                }
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
            // The relay verifies the one-time code and issues the device token; only then is
            // anything stored. (A locally invented token is unknown to the relay: auth_error.)
            val result = pairingClient.pair(
                relayUrl = _uiState.value.relayUrl,
                code = code,
                deviceId = _uiState.value.deviceId,
                deviceName = android.os.Build.MODEL ?: "Android Phone",
                currentDeviceToken = preferences.mobileDeviceToken.firstOrNull()
            )
            when (result) {
                is PairingResult.Success -> {
                    // Storing the token makes the collector above open the authenticated socket.
                    preferences.setMobileDeviceToken(result.deviceToken)
                    _uiState.update {
                        it.copy(isPaired = true, error = null, successMessage = "Appareil appairé avec le relais.")
                    }
                }
                is PairingResult.Failure -> {
                    android.util.Log.w("MobileControlPairing", "event=pairing_failed reason=${result.reason}")
                    _uiState.update { it.copy(successMessage = null, error = result.message) }
                }
            }
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
            // Requested only: the session is shown as active once the relay confirms it.
            _uiState.update { it.copy(error = null, successMessage = "Démarrage demandé pour $targetAppName : en attente de confirmation du relais…") }
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
