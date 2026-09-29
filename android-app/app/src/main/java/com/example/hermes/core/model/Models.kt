package com.example.hermes.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

// ── Auth Models ─────────────────────────────────────────────────────────────

@Serializable
data class AuthStatusResponse(
    @SerialName("auth_enabled") val authEnabled: Boolean = false,
    @SerialName("logged_in") val loggedIn: Boolean = false
)

@Serializable
data class LoginRequest(
    val password: String
)

@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

// ── Profile Models ──────────────────────────────────────────────────────────

@Serializable
data class ProfileInfo(
    val name: String,
    val path: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("gateway_running") val gatewayRunning: Boolean = false,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("has_env") val hasEnv: Boolean = false,
    @SerialName("skill_count") val skillCount: Int = 0
)

@Serializable
data class ProfilesResponse(
    val profiles: List<ProfileInfo> = emptyList(),
    val active: String = "default"
)

@Serializable
data class ActiveProfileResponse(
    val name: String,
    val path: String? = null
)

@Serializable
data class SwitchProfileRequest(
    val name: String
)

@Serializable
data class SwitchProfileResponse(
    val ok: Boolean = false,
    val active: String? = null,
    @SerialName("hermes_home") val hermesHome: String? = null,
    val error: String? = null
)

@Serializable
data class CreateProfileRequest(
    val name: String,
    @SerialName("clone_from") val cloneFrom: String? = null,
    @SerialName("clone_config") val cloneConfig: Boolean = true,
    @SerialName("default_model") val defaultModel: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("base_url") val baseUrl: String? = null
)

@Serializable
data class CreateProfileResponse(
    val ok: Boolean = false,
    val profile: ProfileInfo? = null,
    val error: String? = null
)

@Serializable
data class DeleteProfileRequest(
    val name: String
)

@Serializable
data class DeleteProfileResponse(
    val ok: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

// ── Provider & Environment Variables Models ───────────────────────────────

@Serializable
data class ProviderInfo(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("has_key") val hasKey: Boolean = false,
    val configurable: Boolean = true,
    @SerialName("key_source") val keySource: String = "none",
    val models: List<String> = emptyList()
)

@Serializable
data class ProvidersResponse(
    val providers: List<ProviderInfo> = emptyList(),
    @SerialName("active_profile") val activeProfile: String? = null
)

@Serializable
data class SetProviderKeyRequest(
    val provider: String,
    @SerialName("api_key") val apiKey: String? = null
)

@Serializable
data class ProviderKeyResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

// ── Session Models ──────────────────────────────────────────────────────────

@Serializable
data class SessionSummary(
    @SerialName("session_id") val sessionId: String,
    val title: String = "New Chat",
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("created_at") val createdAt: Double = 0.0,
    @SerialName("updated_at") val updatedAt: Double = 0.0,
    @SerialName("last_message_at") val lastMessageAt: Double? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val profile: String? = null
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionSummary> = emptyList(),
    @SerialName("other_profile_count") val otherProfileCount: Int = 0
)

@Serializable
data class ToolCall(
    val id: String? = null,
    val name: String,
    val args: JsonElement? = null,
    val output: String? = null,
    val duration: Double? = null,
    @SerialName("is_error") val isError: Boolean = false
)


object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return if (decoder is JsonDecoder) {
            when (val element = decoder.decodeJsonElement()) {
                is JsonPrimitive -> element.content
                is JsonArray -> {
                    element.joinToString("\n") { item ->
                        if (item is JsonObject && item.containsKey("text")) {
                            (item["text"] as? JsonPrimitive)?.content ?: item.toString()
                        } else if (item is JsonPrimitive) {
                            item.content
                        } else {
                            item.toString()
                        }
                    }
                }
                is JsonObject -> {
                    (element["text"] as? JsonPrimitive)?.content ?: element.toString()
                }
                else -> element.toString()
            }
        } else {
            decoder.decodeString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class ChatAttachment(
    val filename: String,
    val path: String,
    val mime: String? = null,
    val size: Long? = null,
    @SerialName("is_image") val isImage: Boolean = false
)

@Serializable
data class ChatMessage(
    val role: String = "assistant", // "user", "assistant", "system"
    @Serializable(with = FlexibleStringSerializer::class)
    val content: String = "",
    val timestamp: Double? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("_ts") val ts: Double? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    val attachments: List<ChatAttachment>? = null,
    val reasoning: String? = null
)

@Serializable
data class SessionDetail(
    @SerialName("session_id") val sessionId: String,
    val title: String = "Untitled",
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null,
    @SerialName("created_at") val createdAt: Double = 0.0,
    @SerialName("updated_at") val updatedAt: Double = 0.0,
    val messages: List<ChatMessage> = emptyList(),
    val pinned: Boolean = false,
    val archived: Boolean = false
)

@Serializable
data class SessionDetailResponse(
    val session: SessionDetail
)

@Serializable
data class NewSessionRequest(
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null
)

@Serializable
data class NewSessionResponse(
    @SerialName("session_id") val rawSessionId: String? = null,
    val session: SessionDetail? = null
) {
    val sessionId: String
        get() = rawSessionId ?: session?.sessionId ?: ""
}

// ── Streaming & Turn Models ─────────────────────────────────────────────────

@Serializable
data class ChatStartRequest(
    @SerialName("session_id") val sessionId: String,
    val message: String,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null,
    val attachments: List<ChatAttachment> = emptyList()
)

@Serializable
data class ChatStartResponse(
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    val error: String? = null,
    @SerialName("active_stream_id") val activeStreamId: String? = null
)

@Serializable
data class CancelStreamRequest(
    @SerialName("stream_id") val streamId: String
)

@Serializable
data class SteerStreamRequest(
    @SerialName("stream_id") val streamId: String,
    val message: String
)

@Serializable
data class ApprovalRespondRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("approval_id") val approvalId: String,
    val approved: Boolean
)

@Serializable
data class ClarifyRespondRequest(
    @SerialName("session_id") val sessionId: String,
    val answer: String
)

// ── SSE Event Payloads ──────────────────────────────────────────────────────

@Serializable
data class TokenPayload(
    val text: String = ""
)

@Serializable
data class ReasoningPayload(
    val text: String = ""
)

@Serializable
data class ToolEventPayload(
    @SerialName("event_type") val eventType: String? = "tool.started",
    val name: String,
    val preview: String? = null,
    val args: JsonElement? = null
)

@Serializable
data class ToolCompletePayload(
    @SerialName("event_type") val eventType: String? = "tool.completed",
    val name: String? = null,
    val preview: String? = null,
    val args: JsonElement? = null,
    val duration: Double? = null,
    @SerialName("is_error") val isError: Boolean = false
)

@Serializable
data class ApprovalPayload(
    val id: String = "",
    val tool: String? = null,
    val command: String? = null,
    val description: String? = null
)

@Serializable
data class ClarifyPayload(
    val question: String = "",
    val options: List<String> = emptyList()
)

@Serializable
data class MeteringPayload(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("tokens_in") val tokensIn: Long? = null,
    @SerialName("tokens_out") val tokensOut: Long? = null,
    @SerialName("total_tokens") val totalTokens: Long? = null,
    @SerialName("cost_usd") val costUsd: Double? = null
)

@Serializable
data class CompressingPayload(
    @SerialName("session_id") val sessionId: String? = null,
    val message: String = ""
)

@Serializable
data class WarningPayload(
    val type: String? = null,
    val message: String = ""
)

@Serializable
data class StreamEndPayload(
    @SerialName("session_id") val sessionId: String? = null
)

@Serializable
data class ErrorPayload(
    val message: String? = null,
    val error: String? = null
)

// Sealed representation of SSE events
sealed class HermesSseEvent {
    data class Token(val text: String) : HermesSseEvent()
    data class Reasoning(val text: String) : HermesSseEvent()
    data class ToolStarted(val name: String, val preview: String?, val args: JsonElement?) : HermesSseEvent()
    data class ToolCompleted(val name: String?, val preview: String?, val duration: Double?, val isError: Boolean) : HermesSseEvent()
    data class ApprovalRequested(val payload: ApprovalPayload) : HermesSseEvent()
    data class ClarifyRequested(val payload: ClarifyPayload) : HermesSseEvent()
    data class Metering(val payload: MeteringPayload) : HermesSseEvent()
    data class Compressing(val message: String) : HermesSseEvent()
    data class Warning(val message: String) : HermesSseEvent()
    data class Done(val session: SessionDetail?) : HermesSseEvent()
    data class StreamEnd(val sessionId: String?) : HermesSseEvent()
    data class Cancelled(val message: String) : HermesSseEvent()
    data class Error(val message: String) : HermesSseEvent()
    data object Heartbeat : HermesSseEvent()
}

// ── Workspace & File Models ─────────────────────────────────────────────────

// ── Skills Models ──────────────────────────────────────────────────────────

@Serializable
data class SkillSummary(
    val name: String,
    val description: String = "",
    val category: String? = null
)

@Serializable
data class SkillsResponse(
    val skills: List<SkillSummary> = emptyList(),
    val categories: List<String> = emptyList(),
    val count: Int = 0
)

@Serializable
data class SkillDetailResponse(
    val name: String? = null,
    val description: String? = null,
    val content: String = "",
    val path: String? = null,
    @SerialName("linked_files") val linkedFiles: Map<String, String>? = null
)

@Serializable
data class SaveSkillRequest(
    val name: String,
    val content: String,
    val category: String? = null
)

@Serializable
data class DeleteSkillRequest(
    val name: String
)

// ── Memory Models ──────────────────────────────────────────────────────────

@Serializable
data class MemoryResponse(
    val memory: String = "",
    val user: String = "",
    val soul: String = "",
    @SerialName("memory_path") val memoryPath: String? = null,
    @SerialName("user_path") val userPath: String? = null,
    @SerialName("soul_path") val soulPath: String? = null
)

@Serializable
data class SaveMemoryRequest(
    val section: String, // "memory", "user", "soul"
    val content: String
)

// ── Workspaces Models ──────────────────────────────────────────────────────

@Serializable
data class WorkspaceInfo(
    val name: String,
    val path: String,
    val default: Boolean = false
)

@Serializable
data class WorkspacesResponse(
    val workspaces: List<WorkspaceInfo> = emptyList(),
    val last: String? = null
)

@Serializable
data class AddWorkspaceRequest(
    val path: String,
    val name: String? = null
)

@Serializable
data class RemoveWorkspaceRequest(
    val path: String
)

@Serializable
data class DirectoryEntry(
    val name: String,
    val path: String,
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
    val modified: Double = 0.0
)

@Serializable
data class DirectoryListingResponse(
    val entries: List<DirectoryEntry> = emptyList(),
    val path: String = "."
)

@Serializable
data class UploadResponse(
    val filename: String,
    val path: String,
    val size: Long = 0,
    val mime: String = "application/octet-stream",
    @SerialName("is_image") val isImage: Boolean = false
)

// ── Health Model ────────────────────────────────────────────────────────────

@Serializable
data class HealthResponse(
    val status: String = "unknown",
    val sessions: Int = 0,
    @SerialName("active_streams") val activeStreams: Int = 0,
    @SerialName("active_runs") val activeRuns: Int = 0,
    @SerialName("uptime_seconds") val uptimeSeconds: Double = 0.0
)

// ── YOLO Mode Models ───────────────────────────────────────────────────────

@Serializable
data class YoloStatusResponse(
    @SerialName("yolo_enabled") val yoloEnabled: Boolean = false,
    val ok: Boolean? = null,
    val error: String? = null
)

@Serializable
data class YoloToggleRequest(
    @SerialName("session_id") val sessionId: String,
    val enabled: Boolean
)

// ── Session Recovery & Search Models ────────────────────────────────────────

@Serializable
data class SessionSearchResponse(
    val sessions: List<SessionSummary> = emptyList()
)

@Serializable
data class SessionRepairResponse(
    val ok: Boolean = false,
    val clean: Boolean = false,
    val findings: List<String> = emptyList(),
    val repairs: List<String> = emptyList()
)

// ── Kanban Models ──────────────────────────────────────────────────────────

@Serializable
data class KanbanTask(
    val id: String,
    val title: String,
    val body: String? = null,
    val status: String = "todo",
    val priority: Int = 0,
    val assignee: String? = null,
    val tenant: String? = null,
    @SerialName("age_seconds") val ageSeconds: Double? = null,
    val progress: Double? = null,
    @SerialName("link_counts") val linkCounts: Map<String, Int> = emptyMap(),
    @SerialName("comment_count") val commentCount: Int = 0
)

@Serializable
data class KanbanColumn(
    val name: String,
    val tasks: List<KanbanTask> = emptyList()
)

@Serializable
data class KanbanBoardResponse(
    val columns: List<KanbanColumn> = emptyList(),
    val tenants: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
    @SerialName("latest_event_id") val latestEventId: Int = 0,
    val changed: Boolean = true,
    @SerialName("read_only") val readOnly: Boolean = false
)

@Serializable
data class KanbanBoardMeta(
    val slug: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
    val total: Int = 0
)

@Serializable
data class KanbanBoardsResponse(
    val boards: List<KanbanBoardMeta> = emptyList(),
    val current: String? = "default",
    @SerialName("read_only") val readOnly: Boolean = false
)

@Serializable
data class KanbanCreateTaskRequest(
    val title: String,
    val body: String? = null,
    val status: String? = null,
    val priority: Int = 0,
    val assignee: String? = null
)

@Serializable
data class KanbanUpdateTaskRequest(
    val title: String? = null,
    val body: String? = null,
    val status: String? = null,
    val priority: Int? = null,
    val assignee: String? = null,
    val reason: String? = null
)

@Serializable
data class KanbanTaskResponse(
    val task: KanbanTask,
    @SerialName("read_only") val readOnly: Boolean = false
)

@Serializable
data class KanbanBlockTaskRequest(
    val reason: String = "Blocked via Mobile App"
)

@Serializable
data class KanbanDispatchResponse(
    val spawned: Int = 0,
    val message: String? = null
)


