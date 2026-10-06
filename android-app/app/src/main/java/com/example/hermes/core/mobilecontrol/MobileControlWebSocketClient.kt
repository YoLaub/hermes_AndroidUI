package com.example.hermes.core.mobilecontrol

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import java.util.concurrent.TimeUnit

class MobileControlWebSocketClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) {
    companion object {
        private const val TAG = "MobileControlWS"
        private const val NORMAL_CLOSURE_STATUS = 1000
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Infinite read timeout for long-lived WS
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var clientScope: CoroutineScope? = null
    private var isManuallyDisconnected = false
    private var authFailed = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** True only after the relay answered auth_ok: an open socket alone proves nothing. */
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    var onCommandReceived: ((MobileCommand, (MobileCommandResult) -> Unit) -> Unit)? = null
    var onSessionEndReceived: ((String) -> Unit)? = null
    var onSessionAck: ((MobileSessionStartedAck) -> Unit)? = null
    var onSessionError: ((MobileSessionError) -> Unit)? = null
    var onSessionState: ((MobileSessionState) -> Unit)? = null

    fun connect(
        relayUrl: String,
        deviceId: String,
        deviceToken: String
    ) {
        disconnect()
        isManuallyDisconnected = false
        authFailed = false
        clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val wsUrl = relayUrl.replace("http://", "ws://").replace("https://", "wss://")
            .trimEnd('/') + "/ws/device"

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Device-Token", deviceToken)
            .build()

        // Never log the device token.
        Log.i(TAG, "event=ws_connecting url=$wsUrl device_id=$deviceId")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "event=ws_open device_id=$deviceId http=${response.code}")
                _isConnected.value = true
                _connectionError.value = null

                // Send Auth envelope
                val authMsg = MobileAuthMsg(
                    deviceId = deviceId,
                    deviceToken = deviceToken
                )
                webSocket.send(json.encodeToString(authMsg))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "event=ws_closing device_id=$deviceId code=$code reason=${reason.ifBlank { "-" }}")
                _isConnected.value = false
                _isAuthenticated.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "event=ws_closed device_id=$deviceId code=$code reason=${reason.ifBlank { "-" }} auth_failed=$authFailed")
                _isConnected.value = false
                _isAuthenticated.value = false
                // A relay-initiated close (not an auth refusal) must not strand the phone offline.
                if (!authFailed) scheduleReconnect(relayUrl, deviceId, deviceToken)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "event=ws_failure device_id=$deviceId http=${response?.code ?: "-"} error=${t.javaClass.simpleName}")
                _isConnected.value = false
                _isAuthenticated.value = false
                _connectionError.value = t.message ?: "Connection error (HTTP ${response?.code})"
                scheduleReconnect(relayUrl, deviceId, deviceToken)
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val jsonTree = json.parseToJsonElement(text).jsonObject
            val type = jsonTree["type"]?.jsonPrimitive?.content ?: ""

            when (type) {
                "auth_ok" -> {
                    Log.i(TAG, "event=auth_ok")
                    _isAuthenticated.value = true
                    _connectionError.value = null
                }
                "auth_error" -> {
                    val err = json.decodeFromString<MobileAuthError>(text)
                    Log.w(TAG, "event=auth_error code=${err.errorCode ?: "-"}")
                    authFailed = true
                    _isAuthenticated.value = false
                    _connectionError.value =
                        "Le relais refuse cet appareil (${err.errorCode ?: "auth"}) : appairage requis."
                }
                "session_started_ack" -> {
                    val ack = json.decodeFromString<MobileSessionStartedAck>(text)
                    Log.i(TAG, "event=session_ack session_id=${ack.sessionId} profile=${ack.profile ?: "-"} device_id=${ack.deviceId ?: "-"}")
                    onSessionAck?.invoke(ack)
                }
                "session_error" -> {
                    val err = json.decodeFromString<MobileSessionError>(text)
                    Log.w(TAG, "event=session_error session_id=${err.sessionId ?: "-"} code=${err.errorCode}")
                    onSessionError?.invoke(err)
                }
                "session_state" -> {
                    val state = json.decodeFromString<MobileSessionState>(text)
                    Log.i(TAG, "event=session_state active=${state.active} session_id=${state.sessionId ?: "-"} profile=${state.profile ?: "-"}")
                    onSessionState?.invoke(state)
                }
                "command" -> {
                    val cmd = json.decodeFromString<MobileCommand>(text)
                    onCommandReceived?.invoke(cmd) { result ->
                        sendResult(result)
                    }
                }
                "session_end" -> {
                    val endMsg = json.decodeFromString<MobileSessionEndMsg>(text)
                    onSessionEndReceived?.invoke(endMsg.sessionId)
                }
                "ping" -> {
                    sendJson("{\"protocol\":\"mobile-control/1\",\"type\":\"pong\"}")
                }
                else -> {
                    Log.d(TAG, "Received message of type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming WebSocket message: $text", e)
        }
    }

    /** Returns false (nothing sent) unless the relay has authenticated this device. */
    fun sendSessionStart(session: MobileControlSession): Boolean {
        val startMsg = MobileSessionStartMsg(
            sessionId = session.id,
            targetPackage = session.targetPackage,
            allowedProfile = session.allowedProfile,
            mode = session.mode.name.lowercase(),
            durationSeconds = session.durationSeconds,
            allowScreenshots = session.allowScreenshots,
            allowCalendar = session.allowCalendar
        )
        if (!_isAuthenticated.value) {
            Log.w(TAG, "event=session_start_not_sent session_id=${session.id} reason=not_authenticated")
            return false
        }
        val sent = sendJson(json.encodeToString(startMsg))
        Log.i(TAG, "event=session_start_sent session_id=${session.id} profile=${session.allowedProfile} screenshots=${if (session.allowScreenshots) "on" else "off"} calendar=${if (session.allowCalendar) "on" else "off"} queued=$sent")
        return sent
    }

    fun sendSessionEnd(sessionId: String, reason: String = "user_cancelled") {
        val endMsg = MobileSessionEndMsg(
            sessionId = sessionId,
            reason = reason
        )
        sendJson(json.encodeToString(endMsg))
    }

    fun sendDeviceStatus(statusMsg: MobileDeviceStatusMsg) {
        sendJson(json.encodeToString(statusMsg))
    }

    fun sendResult(result: MobileCommandResult) {
        val jsonStr = json.encodeToString(result)
        sendJson(jsonStr)
    }

    private fun sendJson(jsonStr: String): Boolean {
        val ws = webSocket
        if (ws != null && _isConnected.value) {
            return ws.send(jsonStr)
        }
        return false
    }

    private fun scheduleReconnect(relayUrl: String, deviceId: String, deviceToken: String) {
        if (isManuallyDisconnected) return
        clientScope?.launch {
            delay(5000)
            if (!isManuallyDisconnected && !_isConnected.value) {
                Log.i(TAG, "Attempting WebSocket reconnection...")
                connect(relayUrl, deviceId, deviceToken)
            }
        }
    }

    fun disconnect() {
        isManuallyDisconnected = true
        _isConnected.value = false
        _isAuthenticated.value = false
        try {
            webSocket?.close(NORMAL_CLOSURE_STATUS, "Disconnected by user")
        } catch (_: Exception) {}
        webSocket = null
        clientScope?.cancel()
        clientScope = null
    }
}
