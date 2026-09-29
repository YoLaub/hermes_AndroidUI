package com.example.hermes.features.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KanbanUiState(
    val boards: List<KanbanBoardMeta> = emptyList(),
    val currentBoard: String = "default",
    val columns: List<KanbanColumn> = emptyList(),
    val selectedColumnIndex: Int = 1, // Default to "todo"
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDispatching: Boolean = false,
    val error: String? = null,
    val messageNotice: String? = null,
    val searchQuery: String = "",
    val selectedTask: KanbanTask? = null,
    val showCreateTaskDialog: Boolean = false,
    val knownAssignees: List<String> = emptyList()
)

class KanbanViewModel(
    private val repository: HermesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KanbanUiState())
    val uiState: StateFlow<KanbanUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // 1. Fetch available boards
            val boardsResult = repository.getKanbanBoards()
            var active = "default"
            if (boardsResult.isSuccess) {
                val bResp = boardsResult.getOrThrow()
                active = bResp.current ?: "default"
                _uiState.update { it.copy(boards = bResp.boards, currentBoard = active) }
            }

            // 2. Fetch active board columns & tasks
            val boardResult = repository.getKanbanBoard(active)
            if (boardResult.isSuccess) {
                val bResp = boardResult.getOrThrow()
                _uiState.update {
                    it.copy(
                        columns = bResp.columns,
                        knownAssignees = bResp.assignees,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Impossible de charger le tableau Kanban : ${boardResult.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val current = _uiState.value.currentBoard
            val boardResult = repository.getKanbanBoard(current)
            if (boardResult.isSuccess) {
                val bResp = boardResult.getOrThrow()
                _uiState.update {
                    it.copy(
                        columns = bResp.columns,
                        knownAssignees = bResp.assignees,
                        isRefreshing = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = boardResult.exceptionOrNull()?.localizedMessage
                    )
                }
            }
        }
    }

    fun switchBoard(slug: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val switchResult = repository.switchKanbanBoard(slug)
            if (switchResult.isSuccess) {
                _uiState.update { it.copy(currentBoard = slug) }
                loadData()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Erreur lors du changement de tableau : ${switchResult.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }

    fun selectColumn(index: Int) {
        _uiState.update { it.copy(selectedColumnIndex = index) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectTask(task: KanbanTask?) {
        _uiState.update { it.copy(selectedTask = task) }
    }

    fun showCreateDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateTaskDialog = show) }
    }

    fun createTask(
        title: String,
        body: String?,
        status: String?,
        priority: Int,
        assignee: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val req = KanbanCreateTaskRequest(
                title = title.trim(),
                body = body?.trim()?.ifBlank { null },
                status = status,
                priority = priority,
                assignee = assignee?.trim()?.ifBlank { null }
            )
            val result = repository.createKanbanTask(req, _uiState.value.currentBoard)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        showCreateTaskDialog = false,
                        messageNotice = "Tâche \"$title\" créée avec succès"
                    )
                }
                refresh()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Erreur de création : ${result.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }

    fun moveTask(taskId: String, newStatus: String) {
        viewModelScope.launch {
            val req = KanbanUpdateTaskRequest(status = newStatus)
            val result = repository.updateKanbanTask(taskId, req, _uiState.value.currentBoard)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(messageNotice = "Statut mis à jour vers $newStatus")
                }
                refresh()
            } else {
                _uiState.update {
                    it.copy(error = "Déplacement impossible : ${result.exceptionOrNull()?.localizedMessage}")
                }
            }
        }
    }

    fun blockTask(taskId: String, reason: String) {
        viewModelScope.launch {
            val result = repository.blockKanbanTask(taskId, reason, _uiState.value.currentBoard)
            if (result.isSuccess) {
                _uiState.update { it.copy(messageNotice = "Tâche marquée comme bloquée") }
                refresh()
            } else {
                _uiState.update { it.copy(error = "Action impossible : ${result.exceptionOrNull()?.localizedMessage}") }
            }
        }
    }

    fun unblockTask(taskId: String) {
        viewModelScope.launch {
            val result = repository.unblockKanbanTask(taskId, _uiState.value.currentBoard)
            if (result.isSuccess) {
                _uiState.update { it.copy(messageNotice = "Tâche débloquée") }
                refresh()
            } else {
                _uiState.update { it.copy(error = "Action impossible : ${result.exceptionOrNull()?.localizedMessage}") }
            }
        }
    }

    fun archiveTask(taskId: String) {
        viewModelScope.launch {
            val result = repository.archiveKanbanTask(taskId, _uiState.value.currentBoard)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        selectedTask = null,
                        messageNotice = "Tâche archivée"
                    )
                }
                refresh()
            } else {
                _uiState.update { it.copy(error = "Archivage impossible : ${result.exceptionOrNull()?.localizedMessage}") }
            }
        }
    }

    fun updateTask(
        taskId: String,
        title: String?,
        body: String?,
        status: String?,
        priority: Int?,
        assignee: String?
    ) {
        viewModelScope.launch {
            val req = KanbanUpdateTaskRequest(
                title = title?.trim()?.ifBlank { null },
                body = body?.trim(),
                status = status,
                priority = priority,
                assignee = assignee?.trim()?.ifBlank { null }
            )
            val result = repository.updateKanbanTask(taskId, req, _uiState.value.currentBoard)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        selectedTask = null,
                        messageNotice = "Tâche modifiée avec succès"
                    )
                }
                refresh()
            } else {
                _uiState.update { it.copy(error = "Mise à jour impossible : ${result.exceptionOrNull()?.localizedMessage}") }
            }
        }
    }

    fun dispatch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDispatching = true, error = null) }
            val result = repository.dispatchKanban(_uiState.value.currentBoard)
            if (result.isSuccess) {
                val resp = result.getOrThrow()
                val count = resp.spawned
                _uiState.update {
                    it.copy(
                        isDispatching = false,
                        messageNotice = if (count > 0) "$count tâche(s) lancée(s) avec succès !" else "Aucune tâche prête à être lancée"
                    )
                }
                refresh()
            } else {
                _uiState.update {
                    it.copy(
                        isDispatching = false,
                        error = "Échec du dispatch : ${result.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(messageNotice = null, error = null) }
    }
}
