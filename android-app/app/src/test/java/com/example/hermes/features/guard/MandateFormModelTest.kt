package com.example.hermes.features.guard

import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.features.profiles.MANDATE_DURATIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MandateFormModelTest {
    @Test
    fun `each site chip says what it covers`() {
        assertEquals("ce site seul", hostScope("codeigniter.com"))
        assertEquals("sous-domaines, pas php.net", hostScope("*.php.net"))
    }

    @Test
    fun `durations read short, as on the mockup`() {
        assertEquals(listOf("1 h", "8 h", "24 h", "7 j", "30 j"), MANDATE_DURATIONS.map { it.label })
    }

    @Test
    fun `the expiry is shown in local time`() {
        val now = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
        assertEquals("19 oct. à 22:00", expiryLabel(now, MANDATE_DURATIONS[1], ZoneId.of("Europe/Paris")))
        assertEquals("26 oct. à 13:00", expiryLabel(now, MANDATE_DURATIONS[3], ZoneId.of("Europe/Paris")))
    }

    @Test
    fun `the mandate is for the active profile only, if it has a memory profile`() {
        val profiles = listOf(MemoryProfile(name = "gaston"), MemoryProfile(name = "freya", state = "revoked"))
        assertEquals("gaston", mandateProfileFor("gaston", profiles))
        assertNull(mandateProfileFor("freya", profiles)) // revoked
        assertNull(mandateProfileFor("memtest", profiles)) // no memory profile
    }

    @Test
    fun `a site still being typed and invalid blocks the form first`() {
        assertEquals(MandateBlock.FixSite, mandateBlock(hostDraftError = "x", issueMessage = "Ajoute au moins un site ou un dossier"))
        assertEquals(MandateBlock.Issue("Le libellé doit faire de 1 à 100 caractères"), mandateBlock(null, "Le libellé doit faire de 1 à 100 caractères"))
        assertNull(mandateBlock(null, null))
    }
}
