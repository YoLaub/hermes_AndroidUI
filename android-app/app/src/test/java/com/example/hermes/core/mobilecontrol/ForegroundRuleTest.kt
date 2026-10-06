package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class ForegroundRuleTest {

    @Test
    fun operationsOnTheTargetAppNeedItInTheForeground() {
        for (op in listOf("observe", "click_element", "scroll", "set_text", "back", "screenshot", "tap_xy")) {
            assertTrue(op, CommandSessionGuard.needsTargetInForeground(op))
        }
    }

    @Test
    fun theNativeBridgeNeverTouchesTheTargetAppSoItDoesNotNeedIt() {
        for (op in listOf("calendar_read", "sms_read", "call_log_read", "sms_send", "call_place")) {
            assertFalse(op, CommandSessionGuard.needsTargetInForeground(op))
        }
    }

    @Test
    fun launchingTheAppObviouslyDoesNotNeedItAlreadyInFront() {
        assertFalse(CommandSessionGuard.needsTargetInForeground("launch_app"))
    }

    @Test
    fun anUnknownOperationKeepsTheStrictRule() {
        assertTrue(CommandSessionGuard.needsTargetInForeground("something_new"))
    }

    @Test
    fun calendarChangesDoNotNeedTheTargetAppEither() {
        for (op in listOf("calendar_create", "calendar_update", "calendar_delete")) {
            assertFalse(op, CommandSessionGuard.needsTargetInForeground(op))
        }
    }
}
