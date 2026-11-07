package com.example.hermes

import org.junit.Assert.assertFalse
import org.junit.Test

class EditionTest {
    @Test
    fun theFullEditionIsNeverTheAgentPhone() {
        assertFalse(BuildConfig.AGENT_PHONE)
        assertFalse(BuildConfig.APPLICATION_ID.endsWith(".lite"))
    }
}
