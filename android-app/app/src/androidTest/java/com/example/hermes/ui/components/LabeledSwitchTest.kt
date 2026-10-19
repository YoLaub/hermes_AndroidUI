package com.example.hermes.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.hermes.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class LabeledSwitchTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun the_switch_is_announced_with_its_label() {
        rule.setContent { HermesTheme { LabeledSwitch(checked = true, onCheckedChange = {}, label = "Chrome") } }
        rule.onNode(isToggleable() and hasContentDescription("Chrome")).assertExists()
    }
}
