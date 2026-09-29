package com.example.hermes.core.network

import android.util.Log
import com.example.hermes.core.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class HermesSseClient(
    private val authInterceptor: AuthInterceptor,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
) {
    companion object {
        private const val TAG = "HermesSseClient"
    }

    private val sseOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Infinite read timeout for long-lived SSE stream
        .retryOnConnectionFailure(true)
        .build()

    fun streamChatEvents(baseUrl: String, streamId: String): Flow<HermesSseEvent> = callbackFlow {
        val url = "$baseUrl/api/chat/stream?stream_id=$streamId"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE connection opened for stream $streamId (code: ${response.code})")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val eventType = type ?: ""
                try {
                    when (eventType) {
                        "token" -> {
                            val payload = json.decodeFromString<TokenPayload>(data)
                            trySend(HermesSseEvent.Token(payload.text))
                        }
                        "reasoning" -> {
                            val payload = json.decodeFromString<ReasoningPayload>(data)
                            trySend(HermesSseEvent.Reasoning(payload.text))
                        }
                        "tool" -> {
                            val payload = json.decodeFromString<ToolEventPayload>(data)
                            trySend(HermesSseEvent.ToolStarted(payload.name, payload.preview, payload.args))
                        }
                        "tool_complete" -> {
                            val payload = json.decodeFromString<ToolCompletePayload>(data)
                            trySend(HermesSseEvent.ToolCompleted(payload.name, payload.preview, payload.duration, payload.isError))
                        }
                        "approval" -> {
                            val payload = json.decodeFromString<ApprovalPayload>(data)
                            trySend(HermesSseEvent.ApprovalRequested(payload))
                        }
                        "clarify" -> {
                            val payload = json.decodeFromString<ClarifyPayload>(data)
                            trySend(HermesSseEvent.ClarifyRequested(payload))
                        }
                        "metering" -> {
                            val payload = json.decodeFromString<MeteringPayload>(data)
                            trySend(HermesSseEvent.Metering(payload))
                        }
                        "compressing" -> {
                            val payload = json.decodeFromString<CompressingPayload>(data)
                            trySend(HermesSseEvent.Compressing(payload.message))
                        }
                        "warning" -> {
                            val payload = json.decodeFromString<WarningPayload>(data)
                            trySend(HermesSseEvent.Warning(payload.message))
                        }
                        "done" -> {
                            try {
                                val sDetail = json.decodeFromString<SessionDetailResponse>(data)
                                trySend(HermesSseEvent.Done(sDetail.session))
                            } catch (_: Exception) {
                                trySend(HermesSseEvent.Done(null))
                            }
                        }
                        "stream_end" -> {
                            val payload = try {
                                json.decodeFromString<StreamEndPayload>(data)
                            } catch (_: Exception) { null }
                            trySend(HermesSseEvent.StreamEnd(payload?.sessionId))
                            close()
                        }
                        "cancel" -> {
                            val msg = try {
                                json.decodeFromString<ErrorPayload>(data).message ?: "Cancelled"
                            } catch (_: Exception) { "Cancelled" }
                            trySend(HermesSseEvent.Cancelled(msg))
                            close()
                        }
                        "apperror", "error" -> {
                            val err = try {
                                val p = json.decodeFromString<ErrorPayload>(data)
                                p.error ?: p.message ?: "Unknown error"
                            } catch (_: Exception) { data }
                            trySend(HermesSseEvent.Error(err))
                            close()
                        }
                        else -> {
                            if (data.contains("heartbeat")) {
                                trySend(HermesSseEvent.Heartbeat)
                            } else {
                                Log.d(TAG, "Unhandled SSE event: type=$type data=$data")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SSE event $type: $data", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE closed for stream $streamId")
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE failure for stream $streamId: ${t?.message}, code=${response?.code}", t)
                trySend(HermesSseEvent.Error(t?.message ?: "SSE Connection Failed (HTTP ${response?.code})"))
                close(t)
            }
        }

        val eventSource = EventSources.createFactory(sseOkHttpClient).newEventSource(request, listener)

        awaitClose {
            Log.d(TAG, "Cancelling SSE event source for stream $streamId")
            eventSource.cancel()
        }
    }
}
