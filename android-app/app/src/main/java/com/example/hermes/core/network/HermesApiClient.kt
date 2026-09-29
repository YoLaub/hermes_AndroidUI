package com.example.hermes.core.network

import com.example.hermes.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HermesApiClient(
    val authInterceptor: AuthInterceptor,
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(baseUrl: String): Result<HealthResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/health")
            .get()
            .build()
        executeRequest(request)
    }

    suspend fun checkAuthStatus(baseUrl: String): Result<AuthStatusResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/auth/status")
            .get()
            .build()
        executeRequest(request)
    }

    suspend fun login(baseUrl: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(LoginRequest(password)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .post(body)
            .build()
        executeRequest(request)
    }

    suspend fun logout(baseUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/auth/logout")
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfiles(baseUrl: String): Result<ProfilesResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/profiles")
            .get()
            .build()
        executeRequest(request)
    }

    suspend fun switchProfile(baseUrl: String, name: String): Result<SwitchProfileResponse> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SwitchProfileRequest(name)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/profile/switch")
            .post(body)
            .build()
        executeRequest(request)
    }

    suspend fun getSessions(baseUrl: String, allProfiles: Boolean = false): Result<SessionsResponse> = withContext(Dispatchers.IO) {
        val url = if (allProfiles) "$baseUrl/api/sessions?all_profiles=1" else "$baseUrl/api/sessions"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        executeRequest(request)
    }

    suspend fun getSession(baseUrl: String, sessionId: String, msgLimit: Int = 100): Result<SessionDetail> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/session?session_id=$sessionId&messages=1&msg_limit=$msgLimit"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        try {
            val response: SessionDetailResponse = executeRequestInternal(request)
            Result.success(response.session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun newSession(
        baseUrl: String,
        workspace: String? = null,
        model: String? = null,
        modelProvider: String? = null,
        profile: String? = null
    ): Result<NewSessionResponse> = withContext(Dispatchers.IO) {
        val reqModel = NewSessionRequest(workspace, model, modelProvider, profile)
        val body = json.encodeToString(reqModel).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/session/new")
            .post(body)
            .build()
        executeRequest(request)
    }

    suspend fun deleteSession(baseUrl: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = """{"session_id":"$sessionId"}""".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/session/delete")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun renameSession(baseUrl: String, sessionId: String, title: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = mapOf("session_id" to sessionId, "title" to title)
        val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/session/rename")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun searchSessions(baseUrl: String, query: String, content: Boolean = true): Result<List<SessionSummary>> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val contentFlag = if (content) "1" else "0"
        val request = Request.Builder()
            .url("$baseUrl/api/sessions/search?q=$q&content=$contentFlag&depth=10")
            .get()
            .build()
        val result: Result<SessionSearchResponse> = executeRequest(request)
        result.map { it.sessions }
    }

    suspend fun repairSessions(baseUrl: String): Result<SessionRepairResponse> = withContext(Dispatchers.IO) {
        val body = "{}".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/session/recovery/repair-safe")
            .post(body)
            .build()
        executeRequest(request)
    }

    suspend fun clearSession(baseUrl: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = """{"session_id":"$sessionId"}""".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/session/clear")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun undoSession(baseUrl: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = """{"session_id":"$sessionId"}""".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/session/undo")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun startChatTurn(baseUrl: String, chatRequest: ChatStartRequest): Result<ChatStartResponse> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(chatRequest).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/chat/start")
            .post(body)
            .build()
        executeRequest(request)
    }

    suspend fun cancelStream(baseUrl: String, streamId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(CancelStreamRequest(streamId)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/chat/cancel")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun steerStream(baseUrl: String, streamId: String, message: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SteerStreamRequest(streamId, message)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/chat/steer")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun respondApproval(baseUrl: String, sessionId: String, approvalId: String, approved: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ApprovalRespondRequest(sessionId, approvalId, approved)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/approval/respond")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun respondClarify(baseUrl: String, sessionId: String, answer: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ClarifyRespondRequest(sessionId, answer)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/clarify/respond")
            .post(body)
            .build()
        executeEmptyResponse(request)
    }

    suspend fun getSkills(baseUrl: String, category: String? = null): Result<SkillsResponse> = withContext(Dispatchers.IO) {
        val url = if (category != null) "$baseUrl/api/skills?category=$category" else "$baseUrl/api/skills"
        val request = Request.Builder().url(url).get().build()
        executeRequest(request)
    }

    suspend fun getSkillContent(baseUrl: String, name: String, file: String? = null): Result<SkillDetailResponse> = withContext(Dispatchers.IO) {
        val url = if (file != null) "$baseUrl/api/skills/content?name=$name&file=$file" else "$baseUrl/api/skills/content?name=$name"
        val request = Request.Builder().url(url).get().build()
        executeRequest(request)
    }

    suspend fun saveSkill(baseUrl: String, name: String, content: String, category: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SaveSkillRequest(name, content, category)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url("$baseUrl/api/skills/save").post(body).build()
        executeEmptyResponse(request)
    }

    suspend fun deleteSkill(baseUrl: String, name: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(DeleteSkillRequest(name)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url("$baseUrl/api/skills/delete").post(body).build()
        executeEmptyResponse(request)
    }

    suspend fun getMemory(baseUrl: String): Result<MemoryResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/memory").get().build()
        executeRequest(request)
    }

    suspend fun saveMemory(baseUrl: String, section: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SaveMemoryRequest(section, content)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url("$baseUrl/api/memory/write").post(body).build()
        executeEmptyResponse(request)
    }

    suspend fun getWorkspaces(baseUrl: String): Result<WorkspacesResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/workspaces")
            .get()
            .build()
        executeRequest(request)
    }

    suspend fun addWorkspace(baseUrl: String, path: String, name: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(AddWorkspaceRequest(path, name)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url("$baseUrl/api/workspaces/add").post(body).build()
        executeEmptyResponse(request)
    }

    suspend fun removeWorkspace(baseUrl: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RemoveWorkspaceRequest(path)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url("$baseUrl/api/workspaces/remove").post(body).build()
        executeEmptyResponse(request)
    }

    suspend fun listFiles(baseUrl: String, sessionId: String, path: String = "."): Result<DirectoryListingResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/list?session_id=$sessionId&path=$path")
            .get()
            .build()
        executeRequest(request)
    }

    suspend fun uploadFile(
        baseUrl: String,
        sessionId: String,
        filename: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Result<UploadResponse> = withContext(Dispatchers.IO) {
        val fileBody = fileBytes.toRequestBody(mimeType.toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("session_id", sessionId)
            .addFormDataPart("file", filename, fileBody)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api/upload")
            .post(multipart)
            .build()
        executeRequest(request)
    }

    suspend fun getYoloStatus(baseUrl: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/session/yolo?session_id=$sessionId")
            .get()
            .build()
        val result: Result<YoloStatusResponse> = executeRequest(request)
        result.map { it.yoloEnabled }
    }

    suspend fun setYoloStatus(baseUrl: String, sessionId: String, enabled: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(YoloToggleRequest(sessionId, enabled)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/session/yolo")
            .post(body)
            .build()
        val result: Result<YoloStatusResponse> = executeRequest(request)
        result.map { it.yoloEnabled }
    }

    private inline fun <reified T> executeRequest(request: Request): Result<T> {
        return try {
            val result: T = executeRequestInternal(request)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @PublishedApi
    internal inline fun <reified T> executeRequestInternal(request: Request): T {
        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $bodyString")
            }
            return json.decodeFromString<T>(bodyString)
        }
    }

    private fun executeEmptyResponse(request: Request): Result<Boolean> {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                } else {
                    Result.success(true)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
