package com.example.hermes.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.core.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessionId: String? = null,
    val sessionTitle: String = "Hermes Chat",
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingTokens: String = "",
    val streamingReasoning: String = "",
    val streamingToolCalls: List<ToolCall> = emptyList(),
    val activeStreamId: String? = null,
    val pendingApproval: ApprovalPayload? = null,
    val pendingClarify: ClarifyPayload? = null,
    val compressingNotice: String? = null,
    val warningNotice: String? = null,
    val inputText: String = "",
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val profiles: List<ProfileInfo> = emptyList(),
    val activeProfile: String = "default",
    val sessions: List<SessionSummary> = emptyList(),
    val skills: List<SkillSummary> = emptyList(),
    val selectedSkillDetail: SkillDetailResponse? = null,
    val isSkillsLoading: Boolean = false,
    val memoryData: MemoryResponse? = null,
    val isMemoryLoading: Boolean = false,
    val workspaces: List<WorkspaceInfo> = emptyList(),
    val activeWorkspace: String? = null,
    val isWorkspacesLoading: Boolean = false,
    val yoloEnabled: Boolean = false,
    val serverUrl: String = "",
    val otherProfileCount: Int = 0,
    val showAllProfiles: Boolean = false,
    val isSearchingSessions: Boolean = false,
    val searchResults: List<SessionSummary>? = null,
    val isRepairingSessions: Boolean = false,
    val providers: List<ProviderInfo> = emptyList(),
    val isProvidersLoading: Boolean = false,
    val envEntries: List<ProfileEnvEntry> = emptyList(),
    val isEnvLoading: Boolean = false,
    val isEnvFallback: Boolean = false,
    val envPath: String = "",
    val openbaoUrl: String = "",
    val openbaoToken: String = "",
    val openbaoMount: String = "hermes",
    val isOpenbaoLoading: Boolean = false,
    val openbaoHealth: OpenBaoHealth? = null,
    val openbaoSecrets: List<OpenBaoSecretItem> = emptyList(),
    val openbaoError: String? = null,
    val commands: List<CommandInfo> = emptyList(),
    val isRestartingGateway: Boolean = false,
    val gatewayStatus: GatewayStatusResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val repository: HermesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 0. Load server URL
            val savedUrl = repository.serverUrl.first()
            _uiState.update { it.copy(serverUrl = savedUrl) }

            // 0.1 Load OpenBao configuration
            val obUrl = repository.openbaoUrl.first()
            val obToken = repository.openbaoToken.first()
            val obMount = repository.openbaoMount.first()
            _uiState.update {
                it.copy(
                    openbaoUrl = obUrl,
                    openbaoToken = obToken ?: "",
                    openbaoMount = obMount
                )
            }
            if (obUrl.isNotBlank()) {
                loadOpenbaoHealth()
            }

            // 1. Load active profile
            val currentProfile = repository.activeProfile.first()
            _uiState.update { it.copy(activeProfile = currentProfile) }

            // 1.1 Load available slash commands
            loadCommands()

            // 2. Load profiles list
            val profilesResult = repository.getProfiles()
            if (profilesResult.isSuccess) {
                _uiState.update { it.copy(profiles = profilesResult.getOrThrow().profiles) }
            }

            // 3. Load sessions
            val sessionsResult = repository.getSessions(allProfiles = _uiState.value.showAllProfiles)
            val sessionList = if (sessionsResult.isSuccess) {
                val resp = sessionsResult.getOrThrow()
                _uiState.update {
                    it.copy(
                        sessions = resp.sessions,
                        otherProfileCount = resp.otherProfileCount
                    )
                }
                resp.sessions
            } else {
                emptyList()
            }

            // 4. Prioritize restoring the exact last active session, regardless of profile filter!
            val lastId = repository.lastSessionId.first()
            var loadedSession: SessionDetail? = null

            if (!lastId.isNullOrBlank()) {
                val detailResult = repository.getSession(lastId)
                if (detailResult.isSuccess) {
                    loadedSession = detailResult.getOrThrow()
                }
            }

            // Fallback to first session in list if lastSessionId could not be loaded
            if (loadedSession == null && sessionList.isNotEmpty()) {
                val firstId = sessionList.first().sessionId
                val detailResult = repository.getSession(firstId)
                if (detailResult.isSuccess) {
                    loadedSession = detailResult.getOrThrow()
                }
            }

            if (loadedSession != null) {
                _uiState.update {
                    it.copy(
                        sessionId = loadedSession.sessionId,
                        sessionTitle = loadedSession.title,
                        messages = loadedSession.messages,
                        isLoading = false
                    )
                }
                fetchYoloStatus(loadedSession.sessionId)
            } else {
                // Only create a new session if no session could be loaded at all
                createNewSessionInternal()
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun createNewSessionInternal(): String? {
        val newResult = repository.newSession()
        return if (newResult.isSuccess) {
            val newSess = newResult.getOrThrow()
            val sid = newSess.sessionId
            _uiState.update {
                it.copy(
                    sessionId = sid,
                    sessionTitle = "New Chat",
                    messages = emptyList(),
                    isLoading = false
                )
            }
            loadSessions()
            fetchYoloStatus(sid)
            sid
        } else {
            _uiState.update { it.copy(isLoading = false) }
            null
        }
    }

    fun loadSessions(allProfiles: Boolean = _uiState.value.showAllProfiles) {
        viewModelScope.launch {
            val sessionsResult = repository.getSessions(allProfiles = allProfiles)
            if (sessionsResult.isSuccess) {
                val resp = sessionsResult.getOrThrow()
                _uiState.update {
                    it.copy(
                        sessions = resp.sessions,
                        otherProfileCount = resp.otherProfileCount,
                        showAllProfiles = allProfiles
                    )
                }
            }
        }
    }

    fun toggleShowAllProfiles() {
        val next = !_uiState.value.showAllProfiles
        loadSessions(allProfiles = next)
    }

    fun searchSessionsOnServer(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = null, isSearchingSessions = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingSessions = true) }
            val res = repository.searchSessions(query)
            if (res.isSuccess) {
                _uiState.update { it.copy(searchResults = res.getOrThrow(), isSearchingSessions = false) }
            } else {
                _uiState.update { it.copy(isSearchingSessions = false) }
            }
        }
    }

    fun clearServerSearch() {
        _uiState.update { it.copy(searchResults = null, isSearchingSessions = false) }
    }

    fun repairSessions(onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRepairingSessions = true) }
            val res = repository.repairSessions()
            _uiState.update { it.copy(isRepairingSessions = false) }
            loadSessions()
            val msg = if (res.isSuccess) {
                val rep = res.getOrThrow()
                if (rep.repairs.isNotEmpty()) {
                    "Récupération réussie : ${rep.repairs.size} session(s) restaurée(s)"
                } else {
                    "Toutes les sessions sur le serveur sont intactes."
                }
            } else {
                "Audit serveur : ${res.exceptionOrNull()?.localizedMessage ?: "Aucune anomalie détectée"}"
            }
            onComplete?.invoke(msg)
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val detailResult = repository.getSession(sessionId)
            if (detailResult.isSuccess) {
                val detail = detailResult.getOrThrow()
                _uiState.update {
                    it.copy(
                        sessionId = detail.sessionId,
                        sessionTitle = detail.title,
                        messages = detail.messages,
                        isLoading = false,
                        isStreaming = false,
                        streamingTokens = "",
                        streamingReasoning = "",
                        streamingToolCalls = emptyList()
                    )
                }
                fetchYoloStatus(detail.sessionId)
            } else {
                val errMsg = detailResult.exceptionOrNull()?.localizedMessage ?: "Erreur inconnue"
                _uiState.update { it.copy(isLoading = false, error = "Impossible de charger la conversation : $errMsg") }
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            createNewSessionInternal()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            loadSessions()
            if (_uiState.value.sessionId == sessionId) {
                createNewSession()
            }
        }
    }

    fun switchProfile(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val switchResult = repository.switchProfile(name)
            if (switchResult.isSuccess) {
                _uiState.update { it.copy(activeProfile = name) }
                loadSessions()
                createNewSession()
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun createProfile(
        name: String,
        cloneFrom: String? = null,
        defaultModel: String? = null,
        provider: String? = null,
        apiKey: String? = null,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val req = CreateProfileRequest(
                name = name.trim().lowercase(),
                cloneFrom = cloneFrom?.ifBlank { null },
                cloneConfig = true,
                defaultModel = defaultModel?.ifBlank { null },
                modelProvider = provider?.ifBlank { null },
                apiKey = apiKey?.ifBlank { null }
            )
            val result = repository.createProfile(req)
            if (result.isSuccess) {
                val profilesRes = repository.getProfiles()
                if (profilesRes.isSuccess) {
                    _uiState.update { it.copy(profiles = profilesRes.getOrThrow().profiles) }
                }
                switchProfile(name.trim().lowercase())
                onComplete?.invoke(true, null)
            } else {
                _uiState.update { it.copy(isLoading = false) }
                onComplete?.invoke(false, result.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun deleteProfile(name: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.deleteProfile(name)
            if (result.isSuccess) {
                val profilesRes = repository.getProfiles()
                if (profilesRes.isSuccess) {
                    _uiState.update { it.copy(profiles = profilesRes.getOrThrow().profiles) }
                }
                if (_uiState.value.activeProfile == name) {
                    switchProfile("default")
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                onComplete?.invoke(true, null)
            } else {
                _uiState.update { it.copy(isLoading = false) }
                onComplete?.invoke(false, result.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun loadProviders() {
        loadProfileEnv()
        viewModelScope.launch {
            _uiState.update { it.copy(isProvidersLoading = true) }
            val res = repository.getProviders()
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        providers = res.getOrThrow().providers,
                        isProvidersLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isProvidersLoading = false) }
            }
        }
    }

    fun loadProfileEnv() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEnvLoading = true) }
            val res = repository.getProfileEnv()
            if (res.isSuccess) {
                val data = res.getOrThrow()
                _uiState.update {
                    it.copy(
                        envEntries = data.entries,
                        envPath = data.envPath,
                        isEnvLoading = false,
                        isEnvFallback = data.isFallback
                    )
                }
            } else {
                _uiState.update { it.copy(isEnvLoading = false) }
            }
        }
    }

    fun setProfileEnvVar(key: String, value: String?, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.setProfileEnvVar(key.trim().uppercase(), value)
            if (res.isSuccess) {
                loadProfileEnv()
                loadProviders()
                onComplete?.invoke(true, null)
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "Erreur d'enregistrement"
                onComplete?.invoke(false, errorMsg)
            }
        }
    }

    fun deleteProfileEnvVar(key: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteProfileEnvVar(key.trim().uppercase())
            if (res.isSuccess) {
                loadProfileEnv()
                loadProviders()
                onComplete?.invoke(true, null)
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "Erreur de suppression"
                onComplete?.invoke(false, errorMsg)
            }
        }
    }

    fun setProviderKey(provider: String, apiKey: String?, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.setProviderKey(provider, apiKey)
            if (res.isSuccess) {
                loadProviders()
                loadProfileEnv()
                onComplete?.invoke(true, null)
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "Erreur d'enregistrement de la clé"
                onComplete?.invoke(false, errorMsg)
            }
        }
    }

    fun deleteProviderKey(provider: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteProviderKey(provider)
            if (res.isSuccess) {
                loadProviders()
                loadProfileEnv()
                onComplete?.invoke(true, null)
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "Erreur de suppression de la clé"
                onComplete?.invoke(false, errorMsg)
            }
        }
    }

    // ── OpenBao Secrets Management ──
    fun loadOpenbaoHealth(onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOpenbaoLoading = true, openbaoError = null) }
            val res = repository.checkOpenbaoHealth()
            if (res.isSuccess) {
                val health = res.getOrThrow()
                _uiState.update { it.copy(openbaoHealth = health, isOpenbaoLoading = false) }
                onComplete?.invoke(true, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Erreur de connexion à OpenBao"
                _uiState.update { it.copy(openbaoHealth = null, isOpenbaoLoading = false, openbaoError = err) }
                onComplete?.invoke(false, err)
            }
        }
    }

    fun saveOpenbaoConfig(url: String, token: String, mount: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            repository.setOpenbaoConfig(url.trim(), token.trim(), mount.trim().ifBlank { "secret" })
            _uiState.update {
                it.copy(
                    openbaoUrl = url.trim(),
                    openbaoToken = token.trim(),
                    openbaoMount = mount.trim().ifBlank { "secret" }
                )
            }
            loadOpenbaoHealth { success, err ->
                if (success) {
                    loadOpenbaoSecrets()
                }
                onComplete?.invoke(success, err)
            }
        }
    }

    fun loadOpenbaoSecrets(profile: String? = null) {
        val targetProfile = profile ?: _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update { it.copy(isOpenbaoLoading = true, openbaoError = null) }
            val res = repository.getOpenbaoSecrets(targetProfile)
            if (res.isSuccess) {
                val secretMap = res.getOrThrow()
                val secretItems = secretMap.map { (k, v) -> OpenBaoSecretItem(key = k, value = v) }.sortedBy { it.key }
                _uiState.update {
                    it.copy(
                        openbaoSecrets = secretItems,
                        isOpenbaoLoading = false
                    )
                }
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Erreur de lecture des secrets"
                _uiState.update {
                    it.copy(
                        isOpenbaoLoading = false,
                        openbaoError = err
                    )
                }
            }
        }
    }

    fun saveOpenbaoSecret(key: String, value: String, profile: String? = null, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val targetProfile = profile ?: _uiState.value.activeProfile
        val formattedKey = key.trim().uppercase()
        viewModelScope.launch {
            _uiState.update { it.copy(isOpenbaoLoading = true) }
            val res = repository.saveOpenbaoSecret(targetProfile, formattedKey, value.trim())
            if (res.isSuccess) {
                loadOpenbaoSecrets(targetProfile)
                onComplete?.invoke(true, null)
            } else {
                _uiState.update { it.copy(isOpenbaoLoading = false) }
                val err = res.exceptionOrNull()?.localizedMessage ?: "Erreur d'enregistrement"
                onComplete?.invoke(false, err)
            }
        }
    }

    fun deleteOpenbaoSecret(key: String, profile: String? = null, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val targetProfile = profile ?: _uiState.value.activeProfile
        val formattedKey = key.trim().uppercase()
        viewModelScope.launch {
            _uiState.update { it.copy(isOpenbaoLoading = true) }
            val res = repository.deleteOpenbaoSecret(targetProfile, formattedKey)
            if (res.isSuccess) {
                loadOpenbaoSecrets(targetProfile)
                onComplete?.invoke(true, null)
            } else {
                _uiState.update { it.copy(isOpenbaoLoading = false) }
                val err = res.exceptionOrNull()?.localizedMessage ?: "Erreur de suppression"
                onComplete?.invoke(false, err)
            }
        }
    }

    // ── Commands & Gateway Restart ──
    fun loadCommands() {
        viewModelScope.launch {
            val res = repository.getCommands()
            if (res.isSuccess) {
                _uiState.update { it.copy(commands = res.getOrThrow()) }
            }
        }
    }

    fun restartGateway(profile: String? = null, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val targetProfile = profile ?: _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update { it.copy(isRestartingGateway = true) }
            val res = repository.restartGateway(targetProfile)
            _uiState.update { it.copy(isRestartingGateway = false) }
            if (res.isSuccess) {
                val data = res.getOrThrow()
                val restartNotice = ChatMessage(
                    role = "assistant",
                    content = "🔄 **Passerelle redémarrée ($targetProfile)** : Les variables d'environnement et secrets OpenBao ont été rechargés avec succès."
                )
                _uiState.update { it.copy(messages = it.messages + restartNotice) }
                onComplete?.invoke(true, data.message ?: "Passerelle redémarrée")
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Échec du redémarrage"
                onComplete?.invoke(false, err)
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun removeAttachment(attachment: ChatAttachment) {
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.filter { att -> att.path != attachment.path })
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.pendingAttachments.isEmpty()) return
        if (state.isStreaming) return

        if (text.equals("/restart", ignoreCase = true)) {
            _uiState.update { it.copy(inputText = "") }
            restartGateway()
            return
        }

        viewModelScope.launch {
            var sid = _uiState.value.sessionId
            if (sid.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = true) }
                sid = createNewSessionInternal()
                if (sid.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Impossible de créer une conversation avec le serveur"
                        )
                    }
                    return@launch
                }
            }

            val userMessage = ChatMessage(
                role = "user",
                content = text,
                timestamp = System.currentTimeMillis() / 1000.0,
                attachments = _uiState.value.pendingAttachments.ifEmpty { null }
            )

            // Optimistically add user message
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    inputText = "",
                    pendingAttachments = emptyList(),
                    isStreaming = true,
                    streamingTokens = "",
                    streamingReasoning = "",
                    streamingToolCalls = emptyList(),
                    error = null
                )
            }

            val startResult = repository.startChatTurn(
                sessionId = sid,
                message = text,
                attachments = userMessage.attachments ?: emptyList()
            )

            if (startResult.isSuccess) {
                val startData = startResult.getOrThrow()
                val streamId = startData.streamId
                if (!streamId.isNullOrBlank()) {
                    _uiState.update { it.copy(activeStreamId = streamId) }
                    listenToStream(streamId)
                } else if (startData.error != null) {
                    _uiState.update { it.copy(isStreaming = false, error = startData.error) }
                }
            } else {
                val err = startResult.exceptionOrNull()?.localizedMessage ?: "Failed to start chat turn"
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        error = err
                    )
                }
            }
        }
    }

    private fun listenToStream(streamId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val tokenBuffer = StringBuilder()
            val reasoningBuffer = StringBuilder()
            var lastFlushTime = 0L

            fun flushBuffers(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (force || now - lastFlushTime >= 40) {
                    if (tokenBuffer.isNotEmpty() || reasoningBuffer.isNotEmpty()) {
                        val newTokens = tokenBuffer.toString()
                        val newReasoning = reasoningBuffer.toString()
                        tokenBuffer.clear()
                        reasoningBuffer.clear()
                        _uiState.update { current ->
                            current.copy(
                                streamingTokens = if (newTokens.isNotEmpty()) current.streamingTokens + newTokens else current.streamingTokens,
                                streamingReasoning = if (newReasoning.isNotEmpty()) current.streamingReasoning + newReasoning else current.streamingReasoning
                            )
                        }
                        lastFlushTime = now
                    }
                }
            }

            try {
                repository.streamChatEvents(streamId).collect { event ->
                    when (event) {
                        is HermesSseEvent.Token -> {
                            tokenBuffer.append(event.text)
                            if (event.text.contains("\n") || tokenBuffer.length > 300 || System.currentTimeMillis() - lastFlushTime >= 40) {
                                flushBuffers(force = true)
                            }
                        }
                        is HermesSseEvent.Reasoning -> {
                            reasoningBuffer.append(event.text)
                            if (event.text.contains("\n") || reasoningBuffer.length > 300 || System.currentTimeMillis() - lastFlushTime >= 40) {
                                flushBuffers(force = true)
                            }
                        }
                        is HermesSseEvent.ToolStarted -> {
                            flushBuffers(force = true)
                            val tc = ToolCall(
                                name = event.name,
                                args = event.args,
                                output = null,
                                duration = null,
                                isError = false
                            )
                            _uiState.update { it.copy(streamingToolCalls = it.streamingToolCalls + tc) }
                        }
                        is HermesSseEvent.ToolCompleted -> {
                            flushBuffers(force = true)
                            _uiState.update { state ->
                                val updated = state.streamingToolCalls.toMutableList()
                                val idx = updated.indexOfLast { tc -> event.name == null || tc.name == event.name }
                                if (idx != -1) {
                                    val current = updated[idx]
                                    updated[idx] = current.copy(
                                        duration = event.duration,
                                        isError = event.isError,
                                        output = event.preview
                                    )
                                }
                                state.copy(streamingToolCalls = updated)
                            }
                        }
                        is HermesSseEvent.ApprovalRequested -> {
                            flushBuffers(force = true)
                            _uiState.update { it.copy(pendingApproval = event.payload) }
                        }
                        is HermesSseEvent.ClarifyRequested -> {
                            flushBuffers(force = true)
                            _uiState.update { it.copy(pendingClarify = event.payload) }
                        }
                        is HermesSseEvent.Compressing -> {
                            _uiState.update { it.copy(compressingNotice = event.message) }
                        }
                        is HermesSseEvent.Warning -> {
                            _uiState.update { it.copy(warningNotice = event.message) }
                        }
                        is HermesSseEvent.Done -> {
                            flushBuffers(force = true)
                            event.session?.let { finalSession ->
                                _uiState.update {
                                    it.copy(
                                        messages = finalSession.messages,
                                        streamingTokens = "",
                                        streamingReasoning = "",
                                        streamingToolCalls = emptyList()
                                    )
                                }
                            }
                        }
                        is HermesSseEvent.StreamEnd -> {
                            flushBuffers(force = true)
                            finalizeStreamingMessage()
                        }
                        is HermesSseEvent.Cancelled -> {
                            flushBuffers(force = true)
                            finalizeStreamingMessage()
                        }
                        is HermesSseEvent.Error -> {
                            flushBuffers(force = true)
                            _uiState.update { it.copy(isStreaming = false, error = event.message) }
                            finalizeStreamingMessage()
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                flushBuffers(force = true)
                _uiState.update { it.copy(isStreaming = false, error = e.localizedMessage) }
            } finally {
                flushBuffers(force = true)
                if (_uiState.value.isStreaming) {
                    finalizeStreamingMessage()
                }
            }
        }
    }

    private fun finalizeStreamingMessage() {
        val state = _uiState.value
        val sid = state.sessionId

        if (state.streamingTokens.isNotBlank() || state.streamingToolCalls.isNotEmpty()) {
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = state.streamingTokens,
                timestamp = System.currentTimeMillis() / 1000.0,
                toolCalls = state.streamingToolCalls.ifEmpty { null },
                reasoning = state.streamingReasoning.ifBlank { null }
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMessage,
                    isStreaming = false,
                    streamingTokens = "",
                    streamingReasoning = "",
                    streamingToolCalls = emptyList(),
                    activeStreamId = null,
                    compressingNotice = null,
                    warningNotice = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    streamingTokens = "",
                    streamingReasoning = "",
                    streamingToolCalls = emptyList(),
                    activeStreamId = null,
                    compressingNotice = null,
                    warningNotice = null
                )
            }
        }

        // Resynchronize with server authoritative session state
        if (!sid.isNullOrBlank()) {
            viewModelScope.launch {
                val detailResult = repository.getSession(sid)
                if (detailResult.isSuccess) {
                    val detail = detailResult.getOrThrow()
                    _uiState.update {
                        it.copy(
                            messages = detail.messages,
                            sessionTitle = detail.title
                        )
                    }
                }
                loadSessions()
            }
        } else {
            loadSessions()
        }
    }

    fun cancelStream() {
        val streamId = _uiState.value.activeStreamId ?: return
        viewModelScope.launch {
            repository.cancelStream(streamId)
            streamJob?.cancel()
            finalizeStreamingMessage()
        }
    }

    fun respondApproval(approved: Boolean) {
        val state = _uiState.value
        val sid = state.sessionId ?: return
        val appr = state.pendingApproval ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(pendingApproval = null) }
            repository.respondApproval(sid, appr.id, approved)
        }
    }

    fun respondClarify(answer: String) {
        val state = _uiState.value
        val sid = state.sessionId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(pendingClarify = null) }
            repository.respondClarify(sid, answer)
        }
    }

    fun clearChat() {
        val sid = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            repository.clearSession(sid)
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    // ── YOLO Mode (Auto-Approve) ──
    fun fetchYoloStatus(sessionId: String? = _uiState.value.sessionId) {
        val sid = sessionId ?: return
        viewModelScope.launch {
            val result = repository.getYoloStatus(sid)
            if (result.isSuccess) {
                _uiState.update { it.copy(yoloEnabled = result.getOrThrow()) }
            }
        }
    }

    fun toggleYolo() {
        val sid = _uiState.value.sessionId ?: return
        val target = !_uiState.value.yoloEnabled
        viewModelScope.launch {
            val result = repository.setYoloStatus(sid, target)
            if (result.isSuccess) {
                _uiState.update { it.copy(yoloEnabled = target) }
            } else {
                _uiState.update { it.copy(error = "Erreur YOLO: ${result.exceptionOrNull()?.localizedMessage}") }
            }
        }
    }

    fun enableYoloAndApprove(approvalId: String) {
        val sid = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingApproval = null, yoloEnabled = true) }
            repository.setYoloStatus(sid, true)
            repository.respondApproval(sid, approvalId, true)
        }
    }

    // ── Skills Management ──
    fun loadSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSkillsLoading = true) }
            val res = repository.getSkills()
            if (res.isSuccess) {
                _uiState.update { it.copy(skills = res.getOrThrow().skills, isSkillsLoading = false) }
            } else {
                _uiState.update { it.copy(isSkillsLoading = false) }
            }
        }
    }

    fun loadSkillDetail(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSkillsLoading = true) }
            val res = repository.getSkillContent(name)
            if (res.isSuccess) {
                _uiState.update { it.copy(selectedSkillDetail = res.getOrThrow(), isSkillsLoading = false) }
            } else {
                _uiState.update { it.copy(isSkillsLoading = false) }
            }
        }
    }

    fun clearSkillDetail() {
        _uiState.update { it.copy(selectedSkillDetail = null) }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch {
            repository.deleteSkill(name)
            loadSkills()
            _uiState.update { it.copy(selectedSkillDetail = null) }
        }
    }

    // ── Memory Management ──
    fun loadMemory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isMemoryLoading = true) }
            val res = repository.getMemory()
            if (res.isSuccess) {
                _uiState.update { it.copy(memoryData = res.getOrThrow(), isMemoryLoading = false) }
            } else {
                _uiState.update { it.copy(isMemoryLoading = false) }
            }
        }
    }

    fun saveMemory(section: String, content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMemoryLoading = true) }
            val res = repository.saveMemory(section, content)
            if (res.isSuccess) {
                loadMemory()
            }
            _uiState.update { it.copy(isMemoryLoading = false) }
        }
    }

    // ── Workspaces Management ──
    fun loadWorkspaces() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorkspacesLoading = true) }
            val res = repository.getWorkspaces()
            if (res.isSuccess) {
                val data = res.getOrThrow()
                _uiState.update {
                    it.copy(
                        workspaces = data.workspaces,
                        activeWorkspace = it.activeWorkspace ?: data.last,
                        isWorkspacesLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isWorkspacesLoading = false) }
            }
        }
    }

    fun selectWorkspace(path: String) {
        _uiState.update { it.copy(activeWorkspace = path) }
    }

    fun addWorkspace(path: String, name: String?) {
        viewModelScope.launch {
            repository.addWorkspace(path, name)
            loadWorkspaces()
        }
    }

    fun removeWorkspace(path: String) {
        viewModelScope.launch {
            repository.removeWorkspace(path)
            loadWorkspaces()
        }
    }
}
