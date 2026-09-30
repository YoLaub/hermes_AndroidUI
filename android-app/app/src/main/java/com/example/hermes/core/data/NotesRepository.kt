package com.example.hermes.core.data

import android.content.Context
import com.example.hermes.core.model.AgentNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class NotesRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val notesFile = File(context.filesDir, "agent_notes.json")

    private val _notes = MutableStateFlow<List<AgentNote>>(emptyList())
    val notes: StateFlow<List<AgentNote>> = _notes.asStateFlow()

    suspend fun loadNotes(): List<AgentNote> = withContext(Dispatchers.IO) {
        try {
            if (notesFile.exists()) {
                val content = notesFile.readText()
                if (content.isNotBlank()) {
                    val list = json.decodeFromString<List<AgentNote>>(content)
                    _notes.value = list.sortedWith(compareByDescending<AgentNote> { it.isPinned }.thenByDescending { it.updatedAt })
                    return@withContext _notes.value
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Seed with sample collaborative notes if empty
        if (_notes.value.isEmpty()) {
            val defaultNotes = listOf(
                AgentNote(
                    title = "🎯 Objectifs Prospection Morbihan",
                    content = "Points validés avec **Mario** :\n- Recherche de 6 PME cibles (secteur agro & nautisme)\n- Collecte documentaire sans prise de contact directe\n- Restitution dans le Kanban *growth-commercial*",
                    author = "Yoann",
                    authorRole = "user",
                    tags = listOf("Prospection", "Mario", "Morbihan"),
                    isPinned = true
                ),
                AgentNote(
                    title = "⚙️ Notes d'intégration & CRM",
                    content = "Rappels pour **Gaston** :\n- Synchronisation des accès API\n- Automatisation des exports fiches entreprises\n- Vérification des droits d'écriture",
                    author = "Gaston",
                    authorRole = "agent",
                    tags = listOf("CRM", "Gaston", "Technique"),
                    isPinned = false
                )
            )
            _notes.value = defaultNotes
            saveToFile(defaultNotes)
        }
        return@withContext _notes.value
    }

    suspend fun saveNote(note: AgentNote): AgentNote = withContext(Dispatchers.IO) {
        val current = _notes.value.toMutableList()
        val index = current.indexOfFirst { it.id == note.id }
        val updatedNote = note.copy(updatedAt = System.currentTimeMillis())

        if (index != -1) {
            current[index] = updatedNote
        } else {
            current.add(0, updatedNote)
        }

        val sorted = current.sortedWith(compareByDescending<AgentNote> { it.isPinned }.thenByDescending { it.updatedAt })
        _notes.value = sorted
        saveToFile(sorted)
        return@withContext updatedNote
    }

    suspend fun deleteNote(noteId: String) = withContext(Dispatchers.IO) {
        val current = _notes.value.toMutableList()
        val note = current.find { it.id == noteId }
        // Clean audio file if present
        note?.audioFilePath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        current.removeAll { it.id == noteId }
        _notes.value = current
        saveToFile(current)
    }

    suspend fun togglePin(noteId: String) = withContext(Dispatchers.IO) {
        val current = _notes.value.toMutableList()
        val index = current.indexOfFirst { it.id == noteId }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(isPinned = !item.isPinned, updatedAt = System.currentTimeMillis())
            val sorted = current.sortedWith(compareByDescending<AgentNote> { it.isPinned }.thenByDescending { it.updatedAt })
            _notes.value = sorted
            saveToFile(sorted)
        }
    }

    private fun saveToFile(notes: List<AgentNote>) {
        try {
            val text = json.encodeToString(notes)
            notesFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
