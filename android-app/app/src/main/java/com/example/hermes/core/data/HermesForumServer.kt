package com.example.hermes.core.data

import com.example.hermes.core.model.ChatStartRequest
import com.example.hermes.core.model.HermesSseEvent
import com.example.hermes.features.workspace.forum.ForumServer
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * A room member's turns through the WebUI chat routes. Each member has its own session, created with its profile,
 * so the server runs it as that profile. These sessions never become the chat's "last session".
 */
class HermesForumServer(private val repository: HermesRepository) : ForumServer {
    private val api get() = repository.apiClient

    override suspend fun newSession(profile: String, title: String): Result<String> = runCatching {
        val id = api.newSession(repository.getBaseUrl(), profile = profile).getOrThrow().sessionId
        if (id.isBlank()) throw IOException("No session id")
        api.renameSession(repository.getBaseUrl(), id, title)
        id
    }

    override suspend fun startTurn(sessionId: String, profile: String, message: String): Result<String> = runCatching {
        val response = api.startChatTurn(repository.getBaseUrl(), ChatStartRequest(sessionId = sessionId, message = message, profile = profile)).getOrThrow()
        response.error?.let { throw IOException(it) }
        response.streamId ?: response.activeStreamId ?: throw IOException("No stream id")
    }

    override fun stream(streamId: String): Flow<HermesSseEvent> = repository.streamChatEvents(streamId)

    override suspend fun lastReply(sessionId: String): Result<String?> = runCatching {
        api.getSession(repository.getBaseUrl(), sessionId).getOrThrow().messages.lastOrNull()
            ?.takeIf { it.role == "assistant" }?.content
    }
}
