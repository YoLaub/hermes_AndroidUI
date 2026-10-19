package com.example.hermes.nav

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.hermes.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppNavigationBarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun the_inbox_badge_is_spoken_with_its_count() {
        rule.setContent { HermesTheme { AppNavigationBar(current = Tab.Chat, onSelect = {}, inboxCount = 3) } }
        rule.onNodeWithContentDescription("À traiter, 3 éléments en attente").assertExists()
    }

    @Test
    fun a_live_session_is_spoken_on_the_control_tab() {
        rule.setContent { HermesTheme { AppNavigationBar(current = Tab.Chat, onSelect = {}, controlLive = true) } }
        rule.onNodeWithContentDescription("Contrôle du téléphone, session en cours").assertExists()
    }

    @Test
    fun tapping_a_tab_selects_it() {
        var selected: Tab? = null
        rule.setContent { HermesTheme { AppNavigationBar(current = Tab.Chat, onSelect = { selected = it }) } }
        rule.onNodeWithContentDescription("Agent").performClick()
        assertEquals(Tab.Agent, selected)
    }
}
