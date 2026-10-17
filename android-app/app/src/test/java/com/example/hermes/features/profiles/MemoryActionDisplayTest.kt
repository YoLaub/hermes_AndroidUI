package com.example.hermes.features.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryActionDisplayTest {

    @Test
    fun aKnownKindGetsAReadableLabel() {
        assertEquals("Exécuter une commande", actionKindLabel("run"))
        assertEquals("Ouvrir des adresses web", actionKindLabel("navigate"))
        assertEquals("Utiliser un outil", actionKindLabel("tool"))
    }

    @Test
    fun anUnknownKindIsShownAsIsNotInvented() {
        assertEquals("zzz", actionKindLabel("zzz"))
        assertEquals("Type inconnu", actionKindLabel(null))
    }

    @Test
    fun theUntrustedContentReasonIsExplainedInFrench() {
        val raw = "this session has read untrusted content: it cannot write or run anything alone"
        assertTrue(actionReasonText(raw).startsWith("Cette session a lu du contenu non fiable"))
    }

    @Test
    fun anyOtherReasonIsKeptVerbatim() {
        assertEquals("rm is irreversible", actionReasonText("rm is irreversible"))
        assertEquals("", actionReasonText(null))
    }

    @Test
    fun theShortIdIsThePrefixTheAdminCommandListsToo() {
        assertEquals("1c9eb27b", shortId("1c9eb27b-5212-498a-ad1f-641749940202"))
        assertEquals("ab", shortId("ab"))
    }

    @Test
    fun theRequestTimeIsShownWithoutTheTechnicalNoise() {
        assertEquals("16/10/2026 21:00 UTC", formatRequestedAt("2026-10-16T21:00:00Z"))
        assertEquals("heure inconnue", formatRequestedAt(null))
        assertEquals("not-a-date", formatRequestedAt("not-a-date"))
    }
}
