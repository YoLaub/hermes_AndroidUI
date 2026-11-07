package com.example.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionTest {
    @Test
    fun theLiteEditionIsTheAgentPhoneWithItsOwnApplicationId() {
        assertTrue(BuildConfig.AGENT_PHONE)
        assertEquals("com.example.hermes.lite", BuildConfig.APPLICATION_ID)
    }
}
