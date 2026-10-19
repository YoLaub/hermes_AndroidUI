package com.example.hermes.features.workspace.notes

import androidx.activity.ComponentActivity
import com.example.hermes.a11y.assertAccessibleTargetIsAtLeast48dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.hermes.core.audio.AudioPlayerState
import com.example.hermes.core.model.AgentNote
import com.example.hermes.features.workspace.notes.components.NoteCard
import com.example.hermes.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class NoteCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(note: AgentNote) = rule.setContent {
        HermesTheme {
            NoteCard(
                note = note,
                playerState = AudioPlayerState(),
                onPlayAudio = {}, onPauseAudio = {}, onSeekAudio = {},
                onTogglePin = {}, onEditNote = {}, onDeleteNote = {},
            )
        }
    }

    @Test
    fun the_card_actions_have_48dp_touch_targets() {
        show(AgentNote(title = "Courses", content = "Pain", audioFilePath = "/tmp/a.m4a"))
        listOf("Épingler", "Supprimer", "Play").forEach {
            assertAccessibleTargetIsAtLeast48dp(it)
        }
    }
}
