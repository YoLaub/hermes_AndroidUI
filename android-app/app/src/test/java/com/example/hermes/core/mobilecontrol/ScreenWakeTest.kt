package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

/**
 * A phone dedicated to the agent sleeps between requests: when a command needs the screen, the edition wakes
 * it (and swipes a plain "swipe to unlock" keyguard away). A PIN, pattern or fingerprint is never bypassed.
 */
class ScreenWakeTest {
    private fun plan(
        wakes: Boolean = true,
        op: String = "observe",
        interactive: Boolean,
        locked: Boolean,
        secure: Boolean = false
    ) = ScreenWake.plan(wakes, op, interactive, locked, secure)

    // ── Policy ────────────────────────────────────────────────────────────────

    @Test
    fun onlyTheAgentPhoneEditionWakesTheScreen() {
        assertFalse(ControlPolicy.FULL.wakesScreen)
        assertTrue(ControlPolicy.LITE.wakesScreen)
    }

    @Test
    fun theFullAppNeverTouchesTheScreenState() {
        val p = plan(wakes = false, interactive = false, locked = true)
        assertFalse(p.wake)
        assertFalse(p.swipeToUnlock)
        assertNull(p.blockedCode)
    }

    // ── What needs the screen ─────────────────────────────────────────────────

    @Test
    fun screenCommandsNeedTheScreen() {
        listOf("observe", "screenshot", "click_element", "set_text", "scroll", "back", "tap_xy", "launch_app")
            .forEach { assertTrue(it, ScreenWake.operationNeedsScreen(it)) }
    }

    @Test
    fun dataCommandsLeaveTheScreenAlone() {
        listOf(
            "calendar_read", "sms_read", "call_log_read", "sms_send", "call_place",
            "calendar_create", "calendar_update", "calendar_delete", "end_session"
        ).forEach { assertFalse(it, ScreenWake.operationNeedsScreen(it)) }
    }

    @Test
    fun aDataCommandDoesNotWakeASleepingPhone() {
        val p = plan(op = "sms_read", interactive = false, locked = true)
        assertFalse(p.wake)
        assertFalse(p.swipeToUnlock)
        assertNull(p.blockedCode)
    }

    // ── Screen state ──────────────────────────────────────────────────────────

    @Test
    fun anAwakeUnlockedPhoneIsLeftAlone() {
        val p = plan(interactive = true, locked = false)
        assertFalse(p.wake)
        assertFalse(p.swipeToUnlock)
        assertNull(p.blockedCode)
    }

    @Test
    fun aSleepingPhoneWithoutLockJustWakes() {
        val p = plan(interactive = false, locked = false)
        assertTrue(p.wake)
        assertFalse(p.swipeToUnlock)
        assertNull(p.blockedCode)
    }

    @Test
    fun aSleepingPhoneWithASwipeLockWakesAndSwipes() {
        val p = plan(interactive = false, locked = true, secure = false)
        assertTrue(p.wake)
        assertTrue(p.swipeToUnlock)
        assertNull(p.blockedCode)
    }

    @Test
    fun anAwakePhoneStuckOnASwipeLockSwipesWithoutWaking() {
        val p = plan(interactive = true, locked = true, secure = false)
        assertFalse(p.wake)
        assertTrue(p.swipeToUnlock)
        assertNull(p.blockedCode)
    }

    // ── Secure locks are never bypassed ───────────────────────────────────────

    @Test
    fun aPinLockedPhoneIsReportedLockedAndNotSwiped() {
        val p = plan(interactive = false, locked = true, secure = true)
        assertEquals("DEVICE_LOCKED", p.blockedCode)
        assertFalse(p.swipeToUnlock)
    }

    @Test
    fun aPinLockedPhoneStillWakesSoTheOwnerSeesIt() {
        assertTrue(plan(interactive = false, locked = true, secure = true).wake)
        assertFalse(plan(interactive = true, locked = true, secure = true).wake)
    }

    @Test
    fun aSecurePhoneThatIsAlreadyUnlockedIsFine() {
        val p = plan(interactive = true, locked = false, secure = true)
        assertNull(p.blockedCode)
        assertFalse(p.wake)
    }

    @Test
    fun aSecureLockOnlyBlocksCommandsThatNeedTheScreen() {
        assertNull(plan(op = "calendar_read", interactive = false, locked = true, secure = true).blockedCode)
    }
}
