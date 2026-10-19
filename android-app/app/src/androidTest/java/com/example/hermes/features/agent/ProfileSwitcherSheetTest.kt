package com.example.hermes.features.agent

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import com.example.hermes.core.model.ProfileInfo
import com.example.hermes.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class ProfileSwitcherSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun two_profiles_with_the_same_name_are_both_listed() {
        // The server can return two profiles named "default" (seen on the real instance).
        val profiles = listOf(
            ProfileInfo(name = "default", isDefault = true),
            ProfileInfo(name = "default"),
            ProfileInfo(name = "gaston"),
        )
        rule.setContent {
            HermesTheme { ProfileSwitcherSheet(profiles, activeProfile = "gaston", onSelect = {}, onCreate = {}, onDelete = {}) }
        }
        rule.onAllNodesWithText("Default").assertCountEquals(2)
    }
}
