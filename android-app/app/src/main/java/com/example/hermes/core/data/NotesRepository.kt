package com.example.hermes.core.data

import com.example.hermes.core.model.AgentNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Files of the workspace the notes live in, by workspace-relative path. */
interface WorkspaceFiles {
    /** Names of the files in [dir]; empty when the folder does not exist yet. */
    suspend fun list(dir: String): Result<List<String>>
    suspend fun read(path: String): Result<String>
    suspend fun create(path: String, content: String): Result<Unit>
    suspend fun save(path: String, content: String): Result<Unit>
    suspend fun rename(path: String, newName: String): Result<Unit>
    suspend fun delete(path: String): Result<Unit>
}

/** Where a dictated note's recording sits on this phone; the recording itself is never uploaded. */
interface NoteAudioIndex {
    fun get(notePath: String): String?
    fun set(notePath: String, audioPath: String?)
}

private const val MAX_NOTES = 100

/**
 * The notes shared with the agents: Markdown files of the workspace's [NOTES_DIR] folder, which the agents read
 * and write with their usual file tools. A note's id is its file path.
 */
class NotesRepository(private val files: WorkspaceFiles, private val audio: NoteAudioIndex) {

    private val _notes = MutableStateFlow<List<AgentNote>>(emptyList())
    val notes: StateFlow<List<AgentNote>> = _notes.asStateFlow()

    suspend fun loadNotes(): Result<List<AgentNote>> = runCatching {
        val names = files.list(NOTES_DIR).getOrThrow().filter(::isNoteFile).take(MAX_NOTES)
        val loaded = names.map { name ->
            val path = "$NOTES_DIR/$name"
            parseNote(path, files.read(path).getOrThrow()).copy(audioFilePath = audio.get(path))
        }
        loaded.sortedWith(compareByDescending<AgentNote> { it.isPinned }.thenBy { it.title.lowercase() })
            .also { _notes.value = it }
    }

    /** Creates the note's file, or renames then rewrites it; reloads the list. */
    suspend fun saveNote(note: AgentNote): Result<Unit> = runCatching {
        val existing = _notes.value.firstOrNull { it.id == note.id }
        val wanted = noteFileName(note.title)
        val path = if (existing == null) {
            freePath(wanted).also { files.create(it, renderNote(note)).getOrThrow() }
        } else {
            var current = existing.id
            if (current.substringAfterLast('/') != wanted) {
                val target = freePath(wanted)
                files.rename(current, target.substringAfterLast('/')).getOrThrow()
                audio.get(current)?.let { audio.set(target, it); audio.set(current, null) }
                current = target
            }
            files.save(current, renderNote(note)).getOrThrow()
            current
        }
        note.audioFilePath?.let { audio.set(path, it) }
        loadNotes().getOrThrow()
        Unit
    }

    suspend fun deleteNote(noteId: String): Result<Unit> = runCatching {
        files.delete(noteId).getOrThrow()
        audio.get(noteId)?.let { File(it).delete() }
        audio.set(noteId, null)
        _notes.value = _notes.value.filterNot { it.id == noteId }
    }

    suspend fun togglePin(noteId: String): Result<Unit> = runCatching {
        val note = _notes.value.first { it.id == noteId }
        saveNote(note.copy(isPinned = !note.isPinned)).getOrThrow()
    }

    /** "notes/Title.md", or "notes/Title 2.md" and so on when that name is taken. */
    private fun freePath(fileName: String): String {
        val taken = _notes.value.map { it.id.lowercase() }.toSet()
        val base = fileName.removeSuffix(".md")
        return generateSequence(1) { it + 1 }
            .map { n -> "$NOTES_DIR/" + (if (n == 1) fileName else "$base $n.md") }
            .first { it.lowercase() !in taken }
    }
}
