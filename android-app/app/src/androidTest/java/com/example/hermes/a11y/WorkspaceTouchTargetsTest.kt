package com.example.hermes.a11y

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.hermes.features.workspace.calendar.components.MonthCalendarView
import com.example.hermes.features.workspace.forum.components.ForumInputBar
import com.example.hermes.theme.HermesTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WorkspaceTouchTargetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun calendar_days_have_48dp_targets() {
        rule.setContent {
            HermesTheme {
                MonthCalendarView(
                    currentMonth = YearMonth.of(2026, 10),
                    selectedDate = LocalDate.of(2026, 10, 1),
                    markedDays = emptySet(),
                    onDateSelected = {},
                    onMonthOffset = {},
                )
            }
        }
        assertAccessibleTargetIsAtLeast48dp("15")
    }

    @Test
    fun mention_shortcuts_have_48dp_targets() {
        rule.setContent {
            HermesTheme {
                ForumInputBar(
                    inputText = "",
                    members = listOf("Ada"),
                    onInputTextChanged = {},
                    onAppendMention = {},
                    onSendMessage = {},
                    speakingName = null,
                )
            }
        }
        assertAccessibleTargetIsAtLeast48dp("@Ada")
    }
}
