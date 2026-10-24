package com.example.hermes.features.settings

import com.example.hermes.core.model.OpenBaoHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRulesTest {
    @Test
    fun `the relay address must be an http or https URL with a host`() {
        assertNull(relayUrlError("https://mobile-relay-hermes.john-world.store"))
        assertNull(relayUrlError("http://192.168.1.20:8800"))
        assertEquals(RelayUrlError.Required, relayUrlError(" "))
        assertEquals(RelayUrlError.Scheme, relayUrlError("mobile-relay.example"))
        assertEquals(RelayUrlError.Scheme, relayUrlError("ws://relay.example"))
        assertEquals(RelayUrlError.Host, relayUrlError("https://"))
    }

    @Test
    fun `a pairing code is trimmed and required`() {
        assertEquals("ABC-123", pairingCodeOrNull("  ABC-123 "))
        assertNull(pairingCodeOrNull("   "))
    }

    @Test
    fun `the vault line says how OpenBao is doing`() {
        assertEquals("Non configuré", openBaoStatus("", null))
        assertEquals("Injoignable ou en attente de test", openBaoStatus("https://bao", null))
        assertEquals("Scellé · secrets inaccessibles", openBaoStatus("https://bao", OpenBaoHealth(initialized = true, sealed = true)))
        assertEquals("Non initialisé", openBaoStatus("https://bao", OpenBaoHealth(initialized = false, sealed = true)))
        assertEquals("Connecté · v2.1.0", openBaoStatus("https://bao", OpenBaoHealth(initialized = true, sealed = false, version = "2.1.0")))
        assertEquals("Connecté", openBaoStatus("https://bao", OpenBaoHealth(initialized = true, sealed = false)))
    }
}
