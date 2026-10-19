package com.example.hermes.features.mobilecontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlLabelsTest {
    @Test
    fun `journal statuses read in French`() {
        assertEquals("Démarrée", auditStatusLabel("STARTED"))
        assertEquals("Réussie", auditStatusLabel("SUCCESS"))
        assertEquals("Échouée", auditStatusLabel("FAILED"))
        assertEquals("Refusée", auditStatusLabel("DENIED"))
        assertEquals("Confirmée", auditStatusLabel("CONFIRMED"))
        assertEquals("Arrêtée", auditStatusLabel("STOPPED"))
        assertEquals("WEIRD", auditStatusLabel("WEIRD"))
    }

    @Test
    fun `socket errors become one sentence, the technical part kept apart`() {
        assertEquals(
            RelayError("Connexion au relais interrompue", "Software caused connection abort"),
            relayError("Software caused connection abort"),
        )
        assertEquals(RelayError("Connexion au relais interrompue", null), relayError(null))
    }
}
