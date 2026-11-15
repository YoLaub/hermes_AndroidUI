package com.example.hermes.core.mobilecontrol

/** What to do with the screen before a command runs. [blockedCode] is a normalized relay error code. */
data class WakePlan(val wake: Boolean, val swipeToUnlock: Boolean, val blockedCode: String?) {
    companion object {
        val NOTHING = WakePlan(wake = false, swipeToUnlock = false, blockedCode = null)
    }
}

/**
 * A phone dedicated to the agent sleeps between requests. Whatever the command (the lock check refuses all of them
 * on a locked phone), wake the screen and swipe
 * away a plain "swipe to unlock" keyguard. A PIN, pattern or fingerprint is never bypassed: the command is
 * refused as DEVICE_LOCKED (the screen still wakes, so the owner sees the phone is waiting).
 */
/** A vertical swipe, in pixels. */
data class UnlockSwipe(val x: Int, val fromY: Int, val toY: Int)

object ScreenWake {
    /**
     * The swipe that dismisses a plain keyguard. It must start well above the bottom: measured on a real phone, a
     * swipe starting at 85-91 % of the height lands on the lock icon and the charge text and is ignored, while one
     * starting around 70 % works whatever its speed.
     */
    fun unlockSwipe(widthPx: Int, heightPx: Int) =
        UnlockSwipe(x = widthPx / 2, fromY = (heightPx * 0.70).toInt(), toY = (heightPx * 0.20).toInt())

    fun plan(
        wakesScreen: Boolean,
        interactive: Boolean,
        keyguardLocked: Boolean,
        keyguardSecure: Boolean
    ): WakePlan {
        if (!wakesScreen) return WakePlan.NOTHING
        val wake = !interactive
        if (!keyguardLocked) return WakePlan(wake = wake, swipeToUnlock = false, blockedCode = null)
        return if (keyguardSecure) {
            WakePlan(wake = wake, swipeToUnlock = false, blockedCode = "DEVICE_LOCKED")
        } else {
            WakePlan(wake = wake, swipeToUnlock = true, blockedCode = null)
        }
    }
}
