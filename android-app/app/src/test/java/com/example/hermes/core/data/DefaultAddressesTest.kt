package com.example.hermes.core.data

import com.example.hermes.core.mobilecontrol.DEFAULT_RELAY_URL
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAddressesTest {
    @Test
    fun `a fresh install points at the john-world men domain`() {
        assertEquals("https://hermes.john-world.men", HermesPreferences.DEFAULT_SERVER_URL)
        assertEquals("https://hermes-bao.john-world.men", HermesPreferences.DEFAULT_OPENBAO_URL)
        assertEquals("https://mobile-relay-hermes.john-world.men", DEFAULT_RELAY_URL)
    }
}
