package com.example.hermes.features.workspace.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.OutputStream

data class DocumentEditor(val location: DocumentLocation, val document: DocumentText)
data class FilesState(
    val profile: String = "", val spaces: List<DocumentSpace> = emptyList(),
    val space: String = "", val path: String = ".", val entries: List<DocumentEntry> = emptyList(),
    val editor: DocumentEditor? = null, val busy: Boolean = false, val error: String? = null,
    val notice: String? = null,
)

class FilesViewModel(private val repository: DocumentsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(FilesState())
    val state = mutable.asStateFlow()
    private var task: Job? = null

    private fun run(action: suspend () -> Unit) {
        task?.cancel()
        task = viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null, notice = null) }
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.localizedMessage ?: "Opération impossible") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun openProfile(profile: String) {
        task?.cancel()
        mutable.value = FilesState(profile = profile)
        run {
            val spaces = repository.spaces(profile)
            val space = spaces.firstOrNull()?.id.orEmpty()
            mutable.update { it.copy(spaces = spaces, space = space) }
            if (space.isNotBlank()) refresh()
        }
    }
    private fun location(path: String = state.value.path) = state.value.let { DocumentLocation(it.profile, it.space, path) }
    private suspend fun refresh() {
        val here = location()
        val entries = repository.list(here)
        mutable.update { it.copy(entries = entries) }
    }
    fun navigate(path: String, space: String = state.value.space) = run {
        mutable.update { it.copy(space = space, path = path, editor = null) }; refresh()
    }
    fun reload() = run { refresh() }
    fun child(name: String) = if (state.value.path == ".") name else "${state.value.path}/$name"
    fun edit(name: String) = run {
        val target = location(child(name))
        val document = repository.read(target)
        mutable.update { it.copy(editor = DocumentEditor(target, document)) }
    }
    fun closeEditor() { mutable.update { it.copy(editor = null) } }
    fun save(content: String) {
        val editor = state.value.editor ?: return
        run {
            repository.change(editor.location, "save", mapOf("content" to content, "revision" to editor.document.revision))
            mutable.update { it.copy(editor = null) }; refresh()
        }
    }
    fun create(name: String) = run {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..") { "Nom de fichier invalide" }
        repository.change(location(child(name)), "create", mapOf("content" to "")); refresh()
    }
    /** Revision is captured when opening the destructive action, not after the user's decision. */
    suspend fun prepare(name: String): Pair<DocumentLocation, String> {
        val target = location(child(name))
        return target to repository.revision(target)
    }
    fun rename(target: DocumentLocation, revision: String, name: String) = run {
        repository.change(target, "rename", mapOf("name" to name, "revision" to revision)); refresh()
    }
    fun delete(target: DocumentLocation, revision: String) = run {
        repository.change(target, "delete", mapOf("revision" to revision)); refresh()
    }
    fun downloadLocation(name: String) = location(child(name))
    fun download(target: DocumentLocation, output: suspend () -> OutputStream?) = run {
        val stream = withContext(Dispatchers.IO) { output() } ?: error("Impossible de créer le fichier local")
        stream.use { repository.download(target, it) }
        mutable.update { it.copy(notice = "Fichier enregistré sur le téléphone") }
    }
    fun reportError(message: String) { mutable.update { it.copy(error = message) } }
}
