package com.example.hermes.core.data

import com.example.hermes.core.model.AgentNote
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Notes live as Markdown files in the workspace's notes/ folder; the voice recordings stay on the phone. */
class NotesRepositoryTest {

    private class FakeFiles(val files: MutableMap<String, String> = mutableMapOf(), var reachable: Boolean = true) : WorkspaceFiles {
        val calls = mutableListOf<String>()
        private fun <T> guard(value: () -> T): Result<T> = if (reachable) Result.success(value()) else Result.failure(IOException("offline"))
        override suspend fun list(dir: String) = guard {
            files.keys.filter { it.substringBeforeLast('/') == dir }.map { it.substringAfterLast('/') }
        }
        override suspend fun read(path: String) = guard { files.getValue(path) }
        override suspend fun create(path: String, content: String) = guard { calls += "create $path"; files[path] = content }
        override suspend fun save(path: String, content: String) = guard { calls += "save $path"; files[path] = content }
        override suspend fun rename(path: String, newName: String) = guard {
            calls += "rename $path $newName"
            files[path.substringBeforeLast('/') + "/" + newName] = files.remove(path)!!
        }
        override suspend fun delete(path: String) = guard { calls += "delete $path"; files.remove(path); Unit }
    }

    private class MapAudio : NoteAudioIndex {
        val map = mutableMapOf<String, String>()
        override fun get(notePath: String) = map[notePath]
        override fun set(notePath: String, audioPath: String?) { if (audioPath == null) map.remove(notePath) else map[notePath] = audioPath }
    }

    @Test
    fun `the notes are the Markdown files of the folder, pinned first`() = runBlocking {
        val files = FakeFiles(mutableMapOf(
            "notes/B.md" to "b",
            "notes/A.md" to "a",
            "notes/Épinglée.md" to "---\npinned: true\n---\nc",
            "notes/photo.png" to "",
        ))

        val notes = NotesRepository(files, MapAudio()).loadNotes().getOrThrow()

        assertEquals(listOf("Épinglée", "A", "B"), notes.map { it.title })
    }

    @Test
    fun `an unreachable server is reported, not shown as no notes`() = runBlocking {
        val result = NotesRepository(FakeFiles(reachable = false), MapAudio()).loadNotes()

        assertTrue(result.isFailure)
    }

    @Test
    fun `a new note is a new file named after its title`() = runBlocking {
        val files = FakeFiles(mutableMapOf("notes/Courses.md" to "lait"))
        val repo = NotesRepository(files, MapAudio())
        repo.loadNotes()

        repo.saveNote(AgentNote(title = "Courses", content = "pain", author = "")).getOrThrow()

        assertEquals(listOf("create notes/Courses 2.md"), files.calls)
        assertEquals("pain", files.files["notes/Courses 2.md"])
    }

    @Test
    fun `a retitled note is renamed then saved`() = runBlocking {
        val files = FakeFiles(mutableMapOf("notes/Brouillon.md" to "x"))
        val repo = NotesRepository(files, MapAudio())
        val note = repo.loadNotes().getOrThrow().single()

        repo.saveNote(note.copy(title = "Final", content = "y")).getOrThrow()

        assertEquals(listOf("rename notes/Brouillon.md Final.md", "save notes/Final.md"), files.calls)
        assertEquals("y", files.files["notes/Final.md"])
    }

    @Test
    fun `pin and delete change the file`() = runBlocking {
        val files = FakeFiles(mutableMapOf("notes/A.md" to "a"))
        val repo = NotesRepository(files, MapAudio())
        repo.loadNotes()

        repo.togglePin("notes/A.md").getOrThrow()
        assertTrue(files.files.getValue("notes/A.md").startsWith("---\npinned: true\n"))

        repo.deleteNote("notes/A.md").getOrThrow()
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun `a dictated note keeps its recording on the phone`() = runBlocking {
        val audio = MapAudio()
        val repo = NotesRepository(FakeFiles(), audio)

        repo.saveNote(AgentNote(title = "Vocal", content = "texte", audioFilePath = "/data/rec.m4a", audioDurationSec = 4)).getOrThrow()

        assertEquals("/data/rec.m4a", audio.map["notes/Vocal.md"])
        assertEquals("/data/rec.m4a", repo.loadNotes().getOrThrow().single().audioFilePath)
    }
}
