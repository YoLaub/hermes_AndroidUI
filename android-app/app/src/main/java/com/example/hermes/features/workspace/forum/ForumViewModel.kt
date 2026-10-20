package com.example.hermes.features.workspace.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.model.ForumData
import com.example.hermes.core.model.ForumMessage
import com.example.hermes.core.model.ForumRoom
import com.example.hermes.core.model.HermesSseEvent
import com.example.hermes.core.model.OWNER_NAME
import com.example.hermes.core.model.ReplyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Hermes calls a room needs: a session per member, a turn in it, its stream, and its last answer. */
interface ForumServer {
    suspend fun newSession(profile: String, title: String): Result<String>
    /** Starts a turn in [sessionId], run by [profile]; returns the stream id. */
    suspend fun startTurn(sessionId: String, profile: String, message: String): Result<String>
    fun stream(streamId: String): Flow<HermesSseEvent>
    /** The session's last assistant message, or null if the session has not answered yet. */
    suspend fun lastReply(sessionId: String): Result<String?>
}

/** Where the phone keeps the rooms and their transcripts. */
interface ForumStore {
    suspend fun load(): ForumData
    suspend fun save(data: ForumData)
}

data class ForumUiState(
    val rooms: List<ForumRoom> = emptyList(),
    val selectedRoomId: String? = null,
    val roomMessages: List<ForumMessage> = emptyList(),
    val inputText: String = "",
    val isCreateRoomOpen: Boolean = false,
    /** The member answering right now. */
    val speaking: String? = null,
) {
    val selectedRoom: ForumRoom? get() = rooms.firstOrNull { it.id == selectedRoomId }
}

class ForumViewModel(
    private val server: ForumServer,
    private val store: ForumStore,
    private val displayName: (String) -> String,
) : ViewModel() {

    private var data = ForumData()
    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            data = store.load()
            publish(_uiState.value.selectedRoomId?.takeIf { id -> data.rooms.any { it.id == id } } ?: data.rooms.firstOrNull()?.id)
        }
    }

    fun selectRoom(roomId: String) {
        publish(roomId)
        _uiState.update { it.copy(inputText = "") }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun appendMentionToInput(name: String) {
        val current = _uiState.value.inputText
        val mention = "@$name "
        _uiState.update { it.copy(inputText = if (current.isEmpty() || current.endsWith(" ")) current + mention else "$current $mention") }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val room = _uiState.value.selectedRoom ?: return
        if (text.isBlank() || _uiState.value.speaking != null) return
        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            append(ForumMessage(roomId = room.id, sender = OWNER_NAME, fromOwner = true, content = text))
            for (member in targetsOf(text, room.members, displayName)) {
                _uiState.update { it.copy(speaking = member) }
                append(turnOf(room.id, member))
            }
            _uiState.update { it.copy(speaking = null) }
        }
    }

    /** One member's reply: in its session (created on its first turn), told what the room said since it last spoke. */
    private suspend fun turnOf(roomId: String, member: String): ForumMessage {
        fun failed(error: Throwable, sessionId: String? = null) =
            ForumMessage(roomId = roomId, sender = member, content = error.message.orEmpty(), state = ReplyState.FAILED, sessionId = sessionId)

        val room = data.rooms.first { it.id == roomId }
        val sessionId = room.sessions[member] ?: server.newSession(member, "Forum · ${room.name}")
            .onSuccess { id -> saveRoom(room.copy(sessions = room.sessions + (member to id))) }
            .getOrElse { return failed(it) }
        val prompt = promptFor(room, messagesOf(roomId), member, displayName)
        val streamId = server.startTurn(sessionId, member, prompt).getOrElse { return failed(it, sessionId) }
        val reply = runCatching { collectReply(server.stream(streamId)) }.getOrElse { return failed(it, sessionId) }
        return ForumMessage(roomId = roomId, sender = member, content = reply.text, state = reply.state, sessionId = sessionId)
    }

    /** Fills the replies that were waiting for a decision, once their session has answered. */
    fun refreshWaiting() {
        viewModelScope.launch {
            data.messages.filter { it.state == ReplyState.WAITING && it.sessionId != null }.forEach { waiting ->
                val answer = server.lastReply(waiting.sessionId!!).getOrNull() ?: return@forEach
                if (answer.isNotBlank()) replace(waiting.copy(content = answer, state = ReplyState.DONE))
            }
        }
    }

    fun openCreateRoom() = _uiState.update { it.copy(isCreateRoomOpen = true) }

    fun closeCreateRoom() = _uiState.update { it.copy(isCreateRoomOpen = false) }

    fun createRoom(name: String, topic: String, icon: String, members: List<String>) {
        if (name.isBlank() || members.isEmpty()) return
        val room = ForumRoom(name = name.trim(), topic = topic.trim(), iconEmoji = icon.ifBlank { "🏛️" }, members = members)
        viewModelScope.launch {
            commit(data.copy(rooms = data.rooms + room))
            publish(room.id)
            closeCreateRoom()
        }
    }

    /** Forgets the room and its transcript; the members' sessions stay in the server's history. */
    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            commit(data.copy(rooms = data.rooms.filterNot { it.id == roomId }, messages = data.messages.filterNot { it.roomId == roomId }))
            publish(data.rooms.firstOrNull()?.id)
        }
    }

    private fun messagesOf(roomId: String) = data.messages.filter { it.roomId == roomId }

    private suspend fun append(message: ForumMessage) {
        commit(data.copy(messages = data.messages + message))
        publish(_uiState.value.selectedRoomId)
    }

    private suspend fun replace(message: ForumMessage) {
        commit(data.copy(messages = data.messages.map { if (it.id == message.id) message else it }))
        publish(_uiState.value.selectedRoomId)
    }

    private suspend fun saveRoom(room: ForumRoom) {
        commit(data.copy(rooms = data.rooms.map { if (it.id == room.id) room else it }))
        publish(_uiState.value.selectedRoomId)
    }

    private suspend fun commit(next: ForumData) {
        data = next
        store.save(next)
    }

    private fun publish(selected: String?) {
        _uiState.update { it.copy(rooms = data.rooms, selectedRoomId = selected, roomMessages = selected?.let(::messagesOf).orEmpty()) }
    }
}
