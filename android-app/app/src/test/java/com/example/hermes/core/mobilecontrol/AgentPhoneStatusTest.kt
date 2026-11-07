package com.example.hermes.core.mobilecontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentPhoneStatusTest {
    private fun status(
        paired: Boolean = true, stopped: Boolean = false, authenticated: Boolean = true, accessibilityOn: Boolean = true
    ) = agentPhoneStatus(paired, stopped, authenticated, accessibilityOn)

    @Test
    fun anUnpairedPhoneAsksForPairingWhateverElseIsTrue() {
        assertEquals(AgentPhoneStatus.NOT_PAIRED, status(paired = false, authenticated = true))
    }

    @Test
    fun aPhoneStoppedByTheUserStaysStoppedEvenIfStillConnected() {
        assertEquals(AgentPhoneStatus.STOPPED, status(stopped = true))
    }

    @Test
    fun aPhoneThatTheRelayDoesNotAuthenticateIsOffline() {
        assertEquals(AgentPhoneStatus.OFFLINE, status(authenticated = false))
    }

    @Test
    fun aConnectedPhoneWithoutAccessibilityCannotBeDriven() {
        assertEquals(AgentPhoneStatus.ACCESSIBILITY_OFF, status(accessibilityOn = false))
    }

    @Test
    fun aPairedConnectedPhoneWithAccessibilityIsReadyForTheAgent() {
        assertEquals(AgentPhoneStatus.READY, status())
    }
}
