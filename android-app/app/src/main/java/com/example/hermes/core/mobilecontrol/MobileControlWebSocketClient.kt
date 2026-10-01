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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    var onCommandReceived: ((MobileCommand, (MobileCommandResult) -> Unit) -> Unit)? = null
    var onSessionEndReceived: ((String) -> Unit)? = null

    fun connect(
        relayUrl: String,
        deviceId: String,
        deviceToken: String
    ) {
        disconnect()
        isManuallyDisconnected = false
        clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val wsUrl = relayUrl.replace("http://", "ws://").replace("https://", "wss://")
            .trimEnd('/') + "/ws/device"

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Device-Token", deviceToken)
            .build()

        Log.i(TAG, "Connecting to Mobile Relay WebSocket: $wsUrl (Device: $deviceId)")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Mobile Relay WebSocket connected")
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
                Log.i(TAG, "WebSocket closing: $code / $reason")
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}, HTTP code: ${response?.code}", t)
                _isConnected.value = false
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

    fun sendSessionStart(session: MobileControlSession) {
        val startMsg = MobileSessionStartMsg(
            sessionId = session.id,
            targetPackage = session.targetPackage,
            allowedProfile = session.allowedProfile,
            mode = session.mode.name.lowercase(),
            durationSeconds = session.durationSeconds
        )
        sendJson(json.encodeToString(startMsg))
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
        try {
            webSocket?.close(NORMAL_CLOSURE_STATUS, "Disconnected by user")
        } catch (_: Exception) {}
        webSocket = null
        clientScope?.cancel()
        clientScope = null
    }
}
