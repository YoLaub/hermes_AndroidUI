package com.example.hermes.features.workspace.calendar

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hermes.core.data.CalendarRepository
import com.example.hermes.theme.HermesTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.unit.dp
import java.io.File

class CalendarEmptyDayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Keeps the test's agenda away from the owner's real one. */
    private fun isolatedContext(): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(target.cacheDir, "calendar-test-${System.nanoTime()}").apply { mkdirs() }
        File(dir, "agent_calendar.json").writeText("[]")
        return object : ContextWrapper(target) {
            override fun getFilesDir(): File = dir
        }
    }

    @Test
    fun the_empty_day_hint_stays_above_the_new_slot_button() {
        val viewModel = CalendarViewModel(CalendarRepository(isolatedContext()))
        // In the app the agenda sits between the top bar and the tab bar: about 700 dp on a Pixel 7a.
        rule.setContent { HermesTheme { CalendarScreen(viewModel = viewModel, modifier = Modifier.height(700.dp)) } }
        rule.waitForIdle()

        val hintNode = rule.onNodeWithText("planifier", substring = true)
        val hint = hintNode.getBoundsInRoot()
        val fab = rule.onNodeWithContentDescription("Créneau / RDV").getBoundsInRoot()
        assertTrue("hint bottom ${hint.bottom} overlaps FAB top ${fab.top}", hint.bottom <= fab.top)

        // A text squeezed by its parents is laid out shorter than its lines and overflows.
        val layouts = mutableListOf<TextLayoutResult>()
        hintNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse("hint is cut off", layouts.single().didOverflowHeight)
    }
}
