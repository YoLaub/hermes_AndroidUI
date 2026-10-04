package com.example.hermes.core.accessibility

import org.junit.Assert.*
import org.junit.Test

class TapOutcomeTest {

    @Test
    fun aTapThatWasInjectedIsASuccess() {
        assertNull(TapOutcome.TAPPED.toCommandError("e1"))
    }

    @Test
    fun aTapThatWasRefusedBeforeOrCancelledAfterDispatchIsAnOrdinaryFailureSafeToRetry() {
        for (o in listOf(TapOutcome.REFUSED, TapOutcome.CANCELLED)) {
            val e = o.toCommandError("e1")!!
            assertEquals(o.name, "ACTION_FAILED", e.code)
            assertTrue(e.message.contains("e1"))
        }
    }

    @Test
    fun aTimedOutTapIsReportedAsUnknownAndMustNotBeReplayed() {
        // The gesture may still run after the timeout: reporting a plain failure would invite a double tap.
        val e = TapOutcome.UNKNOWN.toCommandError("e1")!!
        assertEquals("RESULT_UNKNOWN", e.code)
        assertTrue(e.message.contains("e1"))
        assertTrue(e.message.contains("rejou", ignoreCase = true) || e.message.contains("replay", ignoreCase = true))
    }

    @Test
    fun theTargetAppMustStillBeInTheForegroundRightBeforeTheTap() {
        assertTrue(TapGuard.targetStillInForeground("com.linkedin.android", "com.linkedin.android"))
        assertFalse(TapGuard.targetStillInForeground("com.android.systemui", "com.linkedin.android"))   // a dialog or shade
        assertFalse(TapGuard.targetStillInForeground("com.google.android.permissioncontroller", "com.linkedin.android"))
        assertFalse(TapGuard.targetStillInForeground(null, "com.linkedin.android"))
        assertFalse(TapGuard.targetStillInForeground("", "com.linkedin.android"))
    }

    @Test
    fun onlyAnEnabledVisibleElementMayBeTapped() {
        assertTrue(TapGuard.elementTappable(enabled = true, visibleToUser = true))
        assertFalse(TapGuard.elementTappable(enabled = false, visibleToUser = true))   // never tap a disabled "Pay"
        assertFalse(TapGuard.elementTappable(enabled = true, visibleToUser = false))
    }
}
