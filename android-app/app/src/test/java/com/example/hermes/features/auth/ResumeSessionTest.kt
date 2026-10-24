package com.example.hermes.features.auth

import com.example.hermes.core.model.AuthStatusResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** At launch the app goes straight in when the saved session still works, never by logging in on its own. */
class ResumeSessionTest {
    @Test
    fun `a server without a password lets the app in`() {
        assertTrue(canResume(AuthStatusResponse(authEnabled = false, loggedIn = false)))
    }

    @Test
    fun `a still valid session lets the app in`() {
        assertTrue(canResume(AuthStatusResponse(authEnabled = true, loggedIn = true)))
    }

    @Test
    fun `an expired session or an unreachable server shows the sign-in screen`() {
        assertFalse(canResume(AuthStatusResponse(authEnabled = true, loggedIn = false)))
        assertFalse(canResume(null))
    }
}
