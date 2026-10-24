package com.example.hermes.features.workspace.notes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hermes.core.audio.AudioPlayerManager
import com.example.hermes.core.audio.SpeechRecognitionManager
import com.example.hermes.core.data.NoteAudioIndex
import com.example.hermes.core.data.NotesRepository
import com.example.hermes.core.data.WorkspaceFiles
import com.example.hermes.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A note is a file of the server's workspace: the bin asks before deleting it. */
class NoteDeleteConfirmTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class OneFile : WorkspaceFiles {
        val files = mutableMapOf("notes/Courses.md" to "lait")
        override suspend fun list(dir: String) = Result.success(files.keys.map { it.substringAfterLast('/') })
        override suspend fun read(path: String) = Result.success(files.getValue(path))
        override suspend fun create(path: String, content: String) = Result.success(Unit)
        override suspend fun save(path: String, content: String) = Result.success(Unit)
        override suspend fun rename(path: String, newName: String) = Result.success(Unit)
        override suspend fun delete(path: String) = Result.success(Unit).also { files.remove(path) }
    }

    private object NoAudio : NoteAudioIndex {
        override fun get(notePath: String): String? = null
        override fun set(notePath: String, audioPath: String?) = Unit
    }

    @Test
    fun the_bin_asks_before_deleting_the_file() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = OneFile()
        val viewModel = rule.runOnUiThread {
            NotesViewModel(NotesRepository(files, NoAudio), SpeechRecognitionManager(context), AudioPlayerManager(context))
        }
        rule.setContent { HermesTheme { NotesScreen(viewModel = viewModel) } }
        rule.waitUntil(5_000) { viewModel.uiState.value.notes.isNotEmpty() }

        rule.onNodeWithContentDescription("Supprimer").performClick()
        rule.waitForIdle()
        assertEquals(setOf("notes/Courses.md"), files.files.keys)

        rule.onNodeWithText("Supprimer la note").performClick()
        rule.waitUntil(5_000) { files.files.isEmpty() }
    }
}
