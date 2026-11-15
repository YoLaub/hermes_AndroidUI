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
        interactive: Boolean,
        locked: Boolean,
        secure: Boolean = false
    ) = ScreenWake.plan(wakes, interactive, locked, secure)

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

    // ── The swipe that dismisses a plain keyguard ─────────────────────────────

    @Test
    fun theUnlockSwipeStartsAboveTheLockIconAndChargeArea() {
        // Measured on a real phone: a swipe starting at 85-91 % of the height lands on the lock icon / charge text
        // and is ignored; one starting around 70 % dismisses the keyguard.
        val s = ScreenWake.unlockSwipe(widthPx = 720, heightPx = 1640)
        assertEquals(360, s.x)
        assertTrue("starts at ${s.fromY}", s.fromY in (1640 * 0.6).toInt()..(1640 * 0.78).toInt())
        assertTrue(s.toY < s.fromY)
        assertTrue(s.toY > 0)
    }

    @Test
    fun theUnlockSwipeScalesWithTheScreen() {
        val small = ScreenWake.unlockSwipe(widthPx = 480, heightPx = 800)
        assertEquals(240, small.x)
        assertTrue(small.fromY < 800 && small.fromY > small.toY)
    }
}
