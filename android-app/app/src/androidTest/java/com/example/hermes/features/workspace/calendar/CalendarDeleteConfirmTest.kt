package com.example.hermes.features.workspace.calendar

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.hermes.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Deleting removes the event from the phone's real calendar, so it asks first. */
class CalendarDeleteConfirmTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class OneEvent(val event: PhoneEvent) : PhoneCalendar {
        val deleted = mutableListOf<Long>()
        override fun canRead() = true
        override suspend fun events(from: LocalDate, until: LocalDate) = listOf(event).filterNot { it.eventId in deleted }
        override suspend fun create(draft: EventDraft) = true
        override suspend fun update(eventId: Long, draft: EventDraft) = true
        override suspend fun delete(eventId: Long) = true.also { deleted += eventId }
    }

    @Test
    fun the_bin_asks_before_deleting_and_only_the_confirmation_deletes() {
        val today = LocalDate.now()
        val phone = OneEvent(PhoneEvent(9, "Dentiste", today.atTime(9, 0), today.atTime(10, 0), null, false))
        val viewModel = CalendarViewModel(phone, loadRuns = { Result.success(emptyList()) })
        rule.setContent { HermesTheme { CalendarScreen(viewModel = viewModel) } }

        rule.onNodeWithContentDescription("Supprimer").performClick()
        rule.waitForIdle()
        assertEquals(emptyList<Long>(), phone.deleted)

        rule.onNodeWithText("Supprimer de l'agenda").performClick()
        rule.waitForIdle()
        assertEquals(listOf(9L), phone.deleted)
    }
}
