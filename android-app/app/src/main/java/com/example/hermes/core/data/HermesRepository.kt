package com.example.hermes.core.data

import com.example.hermes.core.model.*
import com.example.hermes.core.network.AuthInterceptor
import com.example.hermes.core.network.HermesApiClient
import com.example.hermes.core.network.HermesSseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HermesRepository(
    val preferences: HermesPreferences,
    val authInterceptor: AuthInterceptor = AuthInterceptor(),
    val apiClient: HermesApiClient = HermesApiClient(authInterceptor),
    val sseClient: HermesSseClient = HermesSseClient(authInterceptor),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    init {
        // Wire preferences to AuthInterceptor
        scope.launch {
            authInterceptor.sessionCookie = preferences.sessionCookie.first()
            authInterceptor.activeProfile = preferences.activeProfile.first()

            authInterceptor.onSessionCookieUpdated = { cookie ->
                scope.launch { preferences.setSessionCookie(cookie) }
            }
            authInterceptor.onProfileCookieUpdated = { profile ->
                scope.launch { preferences.setActiveProfile(profile) }
            }
        }
    }

    val serverUrl: Flow<String> = preferences.serverUrl
    val sessionCookie: Flow<String?> = preferences.sessionCookie
    val password: Flow<String?> = preferences.password
    val activeProfile: Flow<String> = preferences.activeProfile
    val lastSessionId: Flow<String?> = preferences.lastSessionId

    suspend fun getBaseUrl(): String = preferences.serverUrl.first()

    suspend fun setServerUrl(url: String) {
        preferences.setServerUrl(url)
    }

    suspend fun setPassword(password: String?) {
        preferences.setPassword(password)
    }

    suspend fun clearPassword() {
        preferences.clearPassword()
    }

    suspend fun checkHealth(): Result<HealthResponse> {
        return apiClient.checkHealth(getBaseUrl())
    }

    suspend fun checkAuthStatus(): Result<AuthStatusResponse> {
        return apiClient.checkAuthStatus(getBaseUrl())
    }

    suspend fun login(password: String): Result<LoginResponse> {
        val result = apiClient.login(getBaseUrl(), password)
        if (result.isSuccess && result.getOrNull()?.ok == true) {
            // Persist the password on successful login
            preferences.setPassword(password)
        }
        return result
    }

    suspend fun logout(): Result<Boolean> {
        val res = apiClient.logout(getBaseUrl())
        preferences.clearSession()
        authInterceptor.sessionCookie = null
        return res
    }

    suspend fun getProfiles(): Result<ProfilesResponse> {
        return apiClient.getProfiles(getBaseUrl())
    }

    suspend fun switchProfile(name: String): Result<SwitchProfileResponse> {
        val res = apiClient.switchProfile(getBaseUrl(), name)
        if (res.isSuccess) {
            preferences.setActiveProfile(name)
            authInterceptor.activeProfile = name
        }
        return res
    }

    suspend fun createProfile(req: CreateProfileRequest): Result<CreateProfileResponse> {
        return apiClient.createProfile(getBaseUrl(), req)
    }

    suspend fun deleteProfile(name: String): Result<DeleteProfileResponse> {
        return apiClient.deleteProfile(getBaseUrl(), name)
    }

    suspend fun getProviders(): Result<ProvidersResponse> {
        return apiClient.getProviders(getBaseUrl())
    }

    suspend fun getProfileEnv(): Result<ProfileEnvResponse> {
        val directRes = apiClient.getProfileEnv(getBaseUrl())
        if (directRes.isSuccess && directRes.getOrThrow().entries.isNotEmpty()) {
            return directRes
        }
        // Fallback: Query getProviders() and convert configured server providers into env entries
        val providersRes = apiClient.getProviders(getBaseUrl())
        if (providersRes.isSuccess) {
            val providers = providersRes.getOrThrow().providers
            val entries = providers.filter { it.hasKey }.map { prov ->
                val envKey = when (prov.id.lowercase()) {
                    "openai" -> "OPENAI_API_KEY"
                    "anthropic" -> "ANTHROPIC_API_KEY"
                    "openrouter" -> "OPENROUTER_API_KEY"
                    "google", "gemini" -> "GEMINI_API_KEY"
                    "groq" -> "GROQ_API_KEY"
                    "mistral" -> "MISTRAL_API_KEY"
                    "deepseek" -> "DEEPSEEK_API_KEY"
                    "together" -> "TOGETHER_API_KEY"
                    "fireworks" -> "FIREWORKS_API_KEY"
                    "cohere" -> "COHERE_API_KEY"
                    "xai" -> "XAI_API_KEY"
                    "perplexity" -> "PERPLEXITY_API_KEY"
                    "nous" -> "NOUS_API_KEY"
                    else -> "${prov.id.uppercase()}_API_KEY"
                }
                ProfileEnvEntry(
                    key = envKey,
                    value = "Clé active (${prov.keySource})",
                    hasValue = true
                )
            }
            if (entries.isNotEmpty() || directRes.isFailure) {
                return Result.success(
                    ProfileEnvResponse(
                        ok = true,
                        profile = preferences.activeProfile.first(),
                        entries = entries,
                        isFallback = directRes.isFailure
                    )
                )
            }
        }
        return directRes
    }

    suspend fun setProfileEnvVar(key: String, value: String?): Result<SetProfileEnvVarResponse> {
        val directRes = apiClient.setProfileEnvVar(getBaseUrl(), key, value)
        if (directRes.isSuccess) {
            return directRes
        }
        // Fallback: If key matches a known standard AI provider, route to /api/providers
        val providerSlug = when (key.trim().uppercase()) {
            "OPENAI_API_KEY", "OPENAI_KEY" -> "openai"
            "ANTHROPIC_API_KEY", "ANTHROPIC_KEY" -> "anthropic"
            "OPENROUTER_API_KEY" -> "openrouter"
            "GEMINI_API_KEY", "GOOGLE_API_KEY" -> "google"
            "GROQ_API_KEY" -> "groq"
            "MISTRAL_API_KEY" -> "mistral"
            "DEEPSEEK_API_KEY" -> "deepseek"
            "TOGETHER_API_KEY" -> "together"
            "FIREWORKS_API_KEY" -> "fireworks"
            "COHERE_API_KEY" -> "cohere"
            "XAI_API_KEY" -> "xai"
            "PERPLEXITY_API_KEY" -> "perplexity"
            "NOUS_API_KEY" -> "nous"
            else -> null
        }
        if (providerSlug != null) {
            val provRes = if (value != null) {
                apiClient.setProviderKey(getBaseUrl(), providerSlug, value)
            } else {
                apiClient.deleteProviderKey(getBaseUrl(), providerSlug)
            }
            if (provRes.isSuccess) {
                return Result.success(
                    SetProfileEnvVarResponse(
                        ok = true,
                        key = key,
                        action = if (value != null) "updated" else "deleted"
                    )
                )
            }
        }
        val rawError = directRes.exceptionOrNull()?.message ?: ""
        if (rawError.contains("404")) {
            return Result.failure(
                java.io.IOException(
                    "L'API officielle Hermes WebUI ne permet que la configuration des clés de providers IA (OpenAI, Anthropic, Gemini, Groq, OpenRouter, Mistral, DeepSeek...). Les variables tierces ($key) pour services, comptes ou MCP doivent être définies dans la section environment de votre docker-compose.yml."
                )
            )
        }
        return directRes
    }

    suspend fun deleteProfileEnvVar(key: String): Result<SetProfileEnvVarResponse> {
        return setProfileEnvVar(key, null)
    }

    suspend fun setProviderKey(provider: String, apiKey: String?): Result<ProviderKeyResponse> {
        return apiClient.setProviderKey(getBaseUrl(), provider, apiKey)
    }

    suspend fun deleteProviderKey(provider: String): Result<ProviderKeyResponse> {
        return apiClient.deleteProviderKey(getBaseUrl(), provider)
    }

    suspend fun getSessions(allProfiles: Boolean = false): Result<SessionsResponse> {
        return apiClient.getSessions(getBaseUrl(), allProfiles)
    }

    suspend fun searchSessions(query: String, content: Boolean = true): Result<List<SessionSummary>> {
        return apiClient.searchSessions(getBaseUrl(), query, content)
    }

    suspend fun repairSessions(): Result<SessionRepairResponse> {
        return apiClient.repairSessions(getBaseUrl())
    }

    suspend fun getSession(sessionId: String): Result<SessionDetail> {
        preferences.setLastSessionId(sessionId)
        return apiClient.getSession(getBaseUrl(), sessionId)
    }

    suspend fun newSession(
        workspace: String? = null,
        model: String? = null,
        modelProvider: String? = null
    ): Result<NewSessionResponse> {
        val profile = preferences.activeProfile.first()
        val res = apiClient.newSession(getBaseUrl(), workspace, model, modelProvider, profile)
        res.getOrNull()?.sessionId?.let {
            preferences.setLastSessionId(it)
        }
        return res
    }

    suspend fun deleteSession(sessionId: String): Result<Boolean> {
        return apiClient.deleteSession(getBaseUrl(), sessionId)
    }

    suspend fun renameSession(sessionId: String, title: String): Result<Boolean> {
        return apiClient.renameSession(getBaseUrl(), sessionId, title)
    }

    suspend fun clearSession(sessionId: String): Result<Boolean> {
        return apiClient.clearSession(getBaseUrl(), sessionId)
    }

    suspend fun undoSession(sessionId: String): Result<Boolean> {
        return apiClient.undoSession(getBaseUrl(), sessionId)
    }

    suspend fun startChatTurn(
        sessionId: String,
        message: String,
        workspace: String? = null,
        model: String? = null,
        attachments: List<ChatAttachment> = emptyList()
    ): Result<ChatStartResponse> {
        val profile = preferences.activeProfile.first()
        val request = ChatStartRequest(
            sessionId = sessionId,
            message = message,
            workspace = workspace,
            model = model,
            profile = profile,
            attachments = attachments
        )
        return apiClient.startChatTurn(getBaseUrl(), request)
    }

    fun streamChatEvents(streamId: String): Flow<HermesSseEvent> {
        var base = "http://10.0.2.2:8000"
        try {
            kotlinx.coroutines.runBlocking { base = getBaseUrl() }
        } catch (_: Exception) {}
        return sseClient.streamChatEvents(base, streamId)
    }

    suspend fun cancelStream(streamId: String): Result<Boolean> {
        return apiClient.cancelStream(getBaseUrl(), streamId)
    }

    suspend fun steerStream(streamId: String, message: String): Result<Boolean> {
        return apiClient.steerStream(getBaseUrl(), streamId, message)
    }

    suspend fun respondApproval(sessionId: String, approvalId: String, approved: Boolean): Result<Boolean> {
        return apiClient.respondApproval(getBaseUrl(), sessionId, approvalId, approved)
    }

    suspend fun respondClarify(sessionId: String, answer: String): Result<Boolean> {
        return apiClient.respondClarify(getBaseUrl(), sessionId, answer)
    }

    suspend fun getYoloStatus(sessionId: String): Result<Boolean> {
        return apiClient.getYoloStatus(getBaseUrl(), sessionId)
    }

    suspend fun setYoloStatus(sessionId: String, enabled: Boolean): Result<Boolean> {
        return apiClient.setYoloStatus(getBaseUrl(), sessionId, enabled)
    }

    suspend fun getSkills(category: String? = null): Result<SkillsResponse> {
        return apiClient.getSkills(getBaseUrl(), category)
    }

    suspend fun getSkillContent(name: String, file: String? = null): Result<SkillDetailResponse> {
        return apiClient.getSkillContent(getBaseUrl(), name, file)
    }

    suspend fun saveSkill(name: String, content: String, category: String? = null): Result<Boolean> {
        return apiClient.saveSkill(getBaseUrl(), name, content, category)
    }

    suspend fun deleteSkill(name: String): Result<Boolean> {
        return apiClient.deleteSkill(getBaseUrl(), name)
    }

    suspend fun getMemory(): Result<MemoryResponse> {
        return apiClient.getMemory(getBaseUrl())
    }

    suspend fun saveMemory(section: String, content: String): Result<Boolean> {
        return apiClient.saveMemory(getBaseUrl(), section, content)
    }

    suspend fun getWorkspaces(): Result<WorkspacesResponse> {
        return apiClient.getWorkspaces(getBaseUrl())
    }

    suspend fun addWorkspace(path: String, name: String? = null): Result<Boolean> {
        return apiClient.addWorkspace(getBaseUrl(), path, name)
    }

    suspend fun removeWorkspace(path: String): Result<Boolean> {
        return apiClient.removeWorkspace(getBaseUrl(), path)
    }

    suspend fun listFiles(sessionId: String, path: String = "."): Result<DirectoryListingResponse> {
        return apiClient.listFiles(getBaseUrl(), sessionId, path)
    }

    suspend fun uploadFile(
        sessionId: String,
        filename: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Result<UploadResponse> {
        return apiClient.uploadFile(getBaseUrl(), sessionId, filename, fileBytes, mimeType)
    }

    // ── Kanban ────────────────────────────────────────────────────────────────

    suspend fun getKanbanBoards(): Result<KanbanBoardsResponse> {
        return apiClient.getKanbanBoards(getBaseUrl())
    }

    suspend fun getKanbanBoard(board: String? = null): Result<KanbanBoardResponse> {
        return apiClient.getKanbanBoard(getBaseUrl(), board)
    }

    suspend fun switchKanbanBoard(slug: String): Result<Boolean> {
        return apiClient.switchKanbanBoard(getBaseUrl(), slug)
    }

    suspend fun createKanbanTask(req: KanbanCreateTaskRequest, board: String? = null): Result<KanbanTaskResponse> {
        return apiClient.createKanbanTask(getBaseUrl(), req, board)
    }

    suspend fun updateKanbanTask(taskId: String, req: KanbanUpdateTaskRequest, board: String? = null): Result<KanbanTaskResponse> {
        return apiClient.updateKanbanTask(getBaseUrl(), taskId, req, board)
    }

    suspend fun blockKanbanTask(taskId: String, reason: String = "Blocked via Mobile App", board: String? = null): Result<KanbanTaskResponse> {
        return apiClient.blockKanbanTask(getBaseUrl(), taskId, reason, board)
    }

    suspend fun unblockKanbanTask(taskId: String, board: String? = null): Result<KanbanTaskResponse> {
        return apiClient.unblockKanbanTask(getBaseUrl(), taskId, board)
    }

    suspend fun archiveKanbanTask(taskId: String, board: String? = null): Result<KanbanTaskResponse> {
        return apiClient.archiveKanbanTask(getBaseUrl(), taskId, board)
    }

    suspend fun dispatchKanban(board: String? = null): Result<KanbanDispatchResponse> {
        return apiClient.dispatchKanban(getBaseUrl(), board)
    }
}

