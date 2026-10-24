package com.example.hermes.features.workspace.calendar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.hermes.theme.HermesTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import androidx.compose.ui.unit.dp

class CalendarEmptyDayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** A phone calendar with nothing in it, so the selected day is empty. */
    private object EmptyPhone : PhoneCalendar {
        override fun canRead() = true
        override suspend fun events(from: LocalDate, until: LocalDate) = emptyList<PhoneEvent>()
        override suspend fun create(draft: EventDraft) = true
        override suspend fun update(eventId: Long, draft: EventDraft) = true
        override suspend fun delete(eventId: Long) = true
    }

    @Test
    fun the_empty_day_hint_stays_above_the_new_slot_button() {
        val viewModel = CalendarViewModel(EmptyPhone, loadRuns = { Result.success(emptyList()) })
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
