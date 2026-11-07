package com.example.hermes.core.mobilecontrol

import com.example.hermes.core.data.HermesPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

const val DEFAULT_RELAY_URL = "https://mobile-relay-hermes.john-world.store"

/**
 * The agent-phone edition's connection: whenever a pairing is stored, keep the relay socket open (the relay opens the
 * session itself). [stop] disconnects until [resume]; that is the phone's only kill switch, and it is deliberate.
 */
class AgentPhoneLink(
    private val manager: MobileControlManager,
    private val preferences: HermesPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _stopped = MutableStateFlow(false)
    val stopped: StateFlow<Boolean> = _stopped.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            if (preferences.mobileDeviceId.firstOrNull().isNullOrBlank()) {
                preferences.setMobileDeviceId("dev_" + UUID.randomUUID().toString().take(8))
            }
            combine(
                preferences.mobileRelayUrl,
                preferences.mobileDeviceId,
                preferences.mobileDeviceToken,
                _stopped
            ) { url, id, token, stopped -> Credentials(url?.takeIf { it.isNotBlank() } ?: DEFAULT_RELAY_URL, id, token, stopped) }
                .distinctUntilChanged()
                .collect { c ->
                    if (c.stopped || c.token.isNullOrBlank() || c.deviceId.isNullOrBlank()) {
                        manager.wsClient.disconnect()
                    } else {
                        manager.wsClient.connect(c.url, c.deviceId, c.token)
                    }
                }
        }
    }

    fun stop() { _stopped.value = true }
    fun resume() { _stopped.value = false }

    private data class Credentials(val url: String, val deviceId: String?, val token: String?, val stopped: Boolean)
}
