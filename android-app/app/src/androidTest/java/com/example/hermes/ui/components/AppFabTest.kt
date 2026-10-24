package com.example.hermes.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.hermes.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class AppFabTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun the_fab_is_announced_with_its_label() {
        rule.setContent { HermesTheme { AppFab(label = "Nouvelle tâche", onClick = {}) } }
        rule.onNode(hasClickAction() and hasContentDescription("Nouvelle tâche")).assertExists()
    }
}
