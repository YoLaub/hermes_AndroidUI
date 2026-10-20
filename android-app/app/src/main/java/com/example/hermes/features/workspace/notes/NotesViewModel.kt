package com.example.hermes.features.workspace.notes

import com.example.hermes.core.model.OWNER_NAME
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.audio.AudioPlayerManager
import com.example.hermes.core.audio.AudioPlayerState
import com.example.hermes.core.audio.SpeechRecognitionManager
import com.example.hermes.core.data.NotesRepository
import com.example.hermes.core.model.AgentNote
import com.example.hermes.core.model.TranscriptionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<AgentNote> = emptyList(),
    val filteredNotes: List<AgentNote> = emptyList(),
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val isEditingNote: Boolean = false,
    val activeEditingNote: AgentNote? = null,
    val isRecordingModalOpen: Boolean = false,
    val preferOfflineRecognition: Boolean = false,
    val transcriptionState: TranscriptionState = TranscriptionState(),
    val playerState: AudioPlayerState = AudioPlayerState(),
    val isLoading: Boolean = false,
    /** The notes could not be read: no session to reach the workspace through, or the server is unreachable. */
    val unavailable: Boolean = false,
    val messageNotice: String? = null
)

class NotesViewModel(
    private val repository: NotesRepository,
    private val speechManager: SpeechRecognitionManager,
    private val audioPlayer: AudioPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        loadNotes()

        // Observe speech recognition state
        viewModelScope.launch {
            speechManager.state.collect { st ->
                _uiState.update { it.copy(transcriptionState = st) }
            }
        }

        // Observe audio player state
        viewModelScope.launch {
            audioPlayer.state.collect { ps ->
                _uiState.update { it.copy(playerState = ps) }
            }
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.loadNotes()
            result.onSuccess(::updateNotesList)
            _uiState.update { it.copy(isLoading = false, unavailable = result.isFailure) }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onTagSelected(tag: String?) {
        _uiState.update { it.copy(selectedTag = if (it.selectedTag == tag) null else tag) }
        applyFilters()
    }

    fun toggleOfflineSpeechRecognition(offline: Boolean) {
        _uiState.update { it.copy(preferOfflineRecognition = offline) }
    }

    // --- Dictaphone / Speech Controls ---

    fun openDictaphoneModal() {
        speechManager.resetState()
        _uiState.update { it.copy(isRecordingModalOpen = true) }
    }

    fun closeDictaphoneModal() {
        if (_uiState.value.transcriptionState.isRecording) {
            speechManager.cancelListening()
        }
        _uiState.update { it.copy(isRecordingModalOpen = false) }
    }

    fun startDictation() {
        speechManager.startListening(
            preferOffline = _uiState.value.preferOfflineRecognition
        )
    }

    fun stopDictation() {
        speechManager.stopListening()
    }

    fun cancelDictation() {
        speechManager.cancelListening()
    }

    fun createNoteFromTranscription() {
        val st = _uiState.value.transcriptionState
        val content = if (st.finalText.isNotBlank()) st.finalText else st.partialText
        if (content.isBlank() && st.audioFilePath == null) return

        val newNote = AgentNote(
            // The title is the file name: no slash, no emoji.
            title = "Note vocale " + java.text.SimpleDateFormat("dd-MM HH'h'mm", java.util.Locale.getDefault()).format(java.util.Date()),
            content = content.ifBlank { "*(Enregistrement audio sans transcription)*" },
            author = OWNER_NAME,
            authorRole = "user",
            tags = listOf("Vocal"),
            audioFilePath = st.audioFilePath,
            audioDurationSec = st.durationSec.takeIf { it > 0 }
        )

        viewModelScope.launch {
            repository.saveNote(newNote)
                .onSuccess {
                    updateNotesList(repository.notes.value)
                    closeDictaphoneModal()
                    _uiState.update { it.copy(messageNotice = "Note vocale enregistrée") }
                }
                .onFailure(::reportFailure)
        }
    }

    // --- Note Editing Controls ---

    fun openNewNote() {
        val note = AgentNote(
            title = "",
            content = "",
            author = OWNER_NAME,
            authorRole = "user",
            tags = emptyList()
        )
        _uiState.update { it.copy(isEditingNote = true, activeEditingNote = note) }
    }

    fun openEditNote(note: AgentNote) {
        _uiState.update { it.copy(isEditingNote = true, activeEditingNote = note) }
    }

    fun closeNoteEditor() {
        _uiState.update { it.copy(isEditingNote = false, activeEditingNote = null) }
    }

    fun saveEditingNote(title: String, content: String, author: String, authorRole: String, tags: List<String>) {
        val current = _uiState.value.activeEditingNote ?: return
        if (title.isBlank() && content.isBlank()) return

        val updated = current.copy(
            title = title.ifBlank { "Sans titre" },
            content = content,
            author = author.ifBlank { OWNER_NAME },
            authorRole = if (author.isBlank()) "user" else authorRole,
            tags = tags
        )

        viewModelScope.launch {
            repository.saveNote(updated)
                .onSuccess {
                    updateNotesList(repository.notes.value)
                    closeNoteEditor()
                }
                .onFailure(::reportFailure)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            if (_uiState.value.playerState.isPlaying) {
                audioPlayer.stop()
            }
            repository.deleteNote(noteId)
                .onSuccess { updateNotesList(repository.notes.value) }
                .onFailure(::reportFailure)
        }
    }

    fun togglePin(noteId: String) {
        viewModelScope.launch {
            repository.togglePin(noteId)
                .onSuccess { updateNotesList(repository.notes.value) }
                .onFailure(::reportFailure)
        }
    }

    // --- Audio Player Controls ---

    fun playAudio(filePath: String) {
        audioPlayer.play(filePath)
    }

    fun pauseAudio() {
        audioPlayer.pause()
    }

    fun seekAudio(progress: Float) {
        audioPlayer.seekTo(progress)
    }

    private fun reportFailure(@Suppress("UNUSED_PARAMETER") error: Throwable) {
        _uiState.update { it.copy(messageNotice = "Échec : les notes n'ont pas pu être enregistrées sur le serveur") }
    }

    fun clearNotice() {
        _uiState.update { it.copy(messageNotice = null) }
    }

    private fun updateNotesList(list: List<AgentNote>) {
        val allTags = list.flatMap { it.tags }.distinct().sorted()
        _uiState.update {
            it.copy(
                notes = list,
                availableTags = allTags
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        val current = _uiState.value
        val query = current.searchQuery.trim().lowercase()
        val tag = current.selectedTag

        val filtered = current.notes.filter { note ->
            val matchesQuery = query.isEmpty() ||
                    note.title.lowercase().contains(query) ||
                    note.content.lowercase().contains(query) ||
                    note.author.lowercase().contains(query)

            val matchesTag = tag == null || note.tags.contains(tag)

            matchesQuery && matchesTag
        }

        _uiState.update { it.copy(filteredNotes = filtered) }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
        audioPlayer.destroy()
    }
}
