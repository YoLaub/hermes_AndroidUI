package com.example.hermes.features.workspace.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.data.ForumRepository
import com.example.hermes.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForumUiState(
    val rooms: List<ForumRoom> = emptyList(),
    val selectedRoom: ForumRoom? = null,
    val roomMessages: List<ForumMessage> = emptyList(),
    val a2aAgents: List<A2AExternalAgent> = emptyList(),
    val inputText: String = "",
    val isCreateRoomOpen: Boolean = false,
    val isA2AManagerOpen: Boolean = false,
    val isAgentTyping: Boolean = false,
    val typingAgentName: String? = null,
    val isLoading: Boolean = false,
    val messageNotice: String? = null
)

class ForumViewModel(
    private val repository: ForumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (rooms, messagesMap, a2a) = repository.loadData()

            val initialRoom = _uiState.value.selectedRoom?.let { cur ->
                rooms.find { it.id == cur.id }
            } ?: rooms.firstOrNull()

            val activeMsgs = initialRoom?.let { messagesMap[it.id] } ?: emptyList()

            _uiState.update {
                it.copy(
                    rooms = rooms,
                    selectedRoom = initialRoom,
                    roomMessages = activeMsgs,
                    a2aAgents = a2a,
                    isLoading = false
                )
            }
        }
    }

    fun selectRoom(roomId: String) {
        val room = _uiState.value.rooms.find { it.id == roomId } ?: return
        val msgs = repository.messages.value[roomId] ?: emptyList()
        _uiState.update {
            it.copy(
                selectedRoom = room,
                roomMessages = msgs,
                inputText = ""
            )
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun appendMentionToInput(agentName: String) {
        val current = _uiState.value.inputText
        val mention = "@$agentName "
        val updated = if (current.endsWith(" ") || current.isEmpty()) current + mention else "$current $mention"
        _uiState.update { it.copy(inputText = updated) }
    }

    fun sendMessage() {
        val currentText = _uiState.value.inputText.trim()
        val currentRoom = _uiState.value.selectedRoom ?: return
        if (currentText.isBlank()) return

        // Extract mentioned agents
        val mentioned = currentRoom.participants
            .filter { currentText.contains("@${it.name}", ignoreCase = true) }
            .map { it.name }

        val userMessage = ForumMessage(
            roomId = currentRoom.id,
            senderName = "Yoann",
            senderType = ParticipantType.USER,
            content = currentText,
            mentionedAgents = mentioned
        )

        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            repository.postMessage(userMessage)
            updateCurrentRoomMessages(currentRoom.id)

            // Trigger collaborative agent responses
            triggerCollaborativeResponses(currentRoom, currentText, mentioned)
        }
    }

    private suspend fun triggerCollaborativeResponses(
        room: ForumRoom,
        userQuery: String,
        mentionedNames: List<String>
    ) {
        val targetParticipants = if (mentionedNames.isNotEmpty()) {
            room.participants.filter { mentionedNames.contains(it.name) }
        } else {
            // If no specific mention, pick the first 1-2 non-user participants for group collaboration
            room.participants.filter { it.type != ParticipantType.USER }.take(2)
        }

        for (participant in targetParticipants) {
            _uiState.update {
                it.copy(isAgentTyping = true, typingAgentName = participant.name)
            }
            delay(1200)

            val replyContent = generateCollaborativeReply(participant, userQuery, room)

            val agentMessage = ForumMessage(
                roomId = room.id,
                senderName = participant.name,
                senderType = participant.type,
                content = replyContent,
                a2aSourceEndpoint = participant.a2aEndpointUrl
            )

            repository.postMessage(agentMessage)
            updateCurrentRoomMessages(room.id)
        }

        _uiState.update {
            it.copy(isAgentTyping = false, typingAgentName = null)
        }
    }

    private fun generateCollaborativeReply(
        participant: ForumParticipant,
        query: String,
        room: ForumRoom
    ): String {
        return when (participant.type) {
            ParticipantType.EXTERNAL_A2A -> {
                "📡 **[A2A Protocol Event]** Réponse reçue de l'agent distant `${participant.name}` via endpoint `${participant.a2aEndpointUrl ?: "https://a2a-node.internal"}` :\n\nJ'ai analysé la demande *\"$query\"*. Les signaux de données ont été mis à disposition du salon `${room.name}`."
            }
            ParticipantType.INTERNAL_AGENT -> {
                when (participant.name.lowercase()) {
                    "mario" -> "Bien reçu @Yoann. Concernant la prospection & growth, je structure les cibles prioritaires et j'ajoute les cartes Kanban dédiées."
                    "gaston" -> "Compris pour l'infrastructure & CRM. Je vérifie les pipelines de synchronisation et je valide les autorisations d'écriture."
                    "hermes" -> "Orchestration validée. Les sous-agents ont synchronisé leurs contextes avec le hub commun."
                    else -> "Message bien pris en compte par ${participant.name}. Je poursuis le traitement collaboratif dans le salon."
                }
            }
            else -> "Reçu."
        }
    }

    private fun updateCurrentRoomMessages(roomId: String) {
        val msgs = repository.messages.value[roomId] ?: emptyList()
        _uiState.update {
            it.copy(
                rooms = repository.rooms.value,
                roomMessages = msgs
            )
        }
    }

    // --- Room Creation & Management ---

    fun openCreateRoom() {
        _uiState.update { it.copy(isCreateRoomOpen = true) }
    }

    fun closeCreateRoom() {
        _uiState.update { it.copy(isCreateRoomOpen = false) }
    }

    fun createRoom(
        name: String,
        topic: String,
        icon: String,
        participants: List<ForumParticipant>,
        isA2AEnabled: Boolean
    ) {
        if (name.isBlank()) return
        val newRoom = ForumRoom(
            name = name,
            topic = topic,
            iconEmoji = icon.ifBlank { "🏛️" },
            participants = participants,
            isA2AEnabled = isA2AEnabled
        )

        viewModelScope.launch {
            repository.saveRoom(newRoom)
            loadData()
            selectRoom(newRoom.id)
            closeCreateRoom()
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
            loadData()
        }
    }

    // --- A2A External Agent Management ---

    fun openA2AManager() {
        _uiState.update { it.copy(isA2AManagerOpen = true) }
    }

    fun closeA2AManager() {
        _uiState.update { it.copy(isA2AManagerOpen = false) }
    }

    fun saveA2AAgent(agent: A2AExternalAgent) {
        viewModelScope.launch {
            repository.saveA2AAgent(agent)
            loadData()
            _uiState.update { it.copy(messageNotice = "Agent A2A '${agent.name}' enregistré") }
        }
    }

    fun deleteA2AAgent(agentId: String) {
        viewModelScope.launch {
            repository.deleteA2AAgent(agentId)
            loadData()
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(messageNotice = null) }
    }
}
