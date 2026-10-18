package com.example.hermes.features.profiles

import com.example.hermes.core.model.MemoryMandate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MemoryMandatesLogicTest {

    // ── hosts ──
    @Test
    fun aPlainHostIsThatHostOnlyAndIsLowercased() {
        assertEquals("site.org", parseMandateHost("  Site.ORG ", subdomains = false).host)
    }

    @Test
    fun subdomainsAreASeparateEntryThatDoesNotCoverTheHostItself() {
        assertEquals("*.site.org", parseMandateHost("site.org", subdomains = true).host)
    }

    @Test
    fun aWildcardTypedByHandIsNotDoubled() {
        assertEquals("*.site.org", parseMandateHost("*.site.org", subdomains = true).host)
        assertEquals("*.site.org", parseMandateHost("*.site.org", subdomains = false).host)
    }

    @Test
    fun whatIsNotAPublicHostNameIsRefusedWithAReason() {
        listOf(
            "https://site.org", "site.org/path", "site.org:8080", "localhost", "10.0.0.1", "192.168.1.5",
            "printer.local", "nas.lan", "db.internal", "a b.org", "", "-a.org", "site..org", "*.org.*"
        ).forEach { bad ->
            val parsed = parseMandateHost(bad, subdomains = false)
            assertNull("$bad should be refused", parsed.host)
            assertTrue("$bad needs a reason", !parsed.error.isNullOrBlank())
        }
    }

    // ── folders ──
    @Test
    fun anAbsoluteFolderIsKeptAndTidied() {
        assertEquals("/home/g/notes", parseMandateFolder(" /home/g//notes/ ").folder)
    }

    @Test
    fun relativeRootParentAndHomeShortcutsAreRefused() {
        listOf("", "notes", "/", "//", "/a/../b", "..", "~", "~/x", "/a/./b").forEach { bad ->
            assertNull("$bad should be refused", parseMandateFolder(bad).folder)
            assertTrue(!parseMandateFolder(bad).error.isNullOrBlank())
        }
    }

    // ── duration ──
    @Test
    fun presetsAreOneHourEightHoursADayAWeekAndThirtyDays() {
        assertEquals(listOf(1L, 8L, 24L, 168L, 720L), MANDATE_DURATIONS.map { it.hours })
        assertEquals(30L * 24, MANDATE_DURATIONS.maxOf { it.hours })
    }

    @Test
    fun theEndIsUtcWithAnExplicitOffset() {
        // 2026-10-19T10:00:00Z + 24 h
        val now = 1_792_404_000_000L
        assertEquals("2026-10-20T10:00:00+00:00", mandateExpiresAt(now, MANDATE_DURATIONS[2]))
        assertEquals("2026-10-19T11:00:00+00:00", mandateExpiresAt(now, MANDATE_DURATIONS[0]))
    }

    // ── form ──
    @Test
    fun aMandateNeedsALabelAndAtLeastOneHostOrFolder() {
        assertEquals(MandateFormIssue.NO_PROFILE, mandateFormIssue(null, "x", listOf("a.org"), emptyList()))
        assertEquals(MandateFormIssue.LABEL, mandateFormIssue("g", "  ", listOf("a.org"), emptyList()))
        assertEquals(MandateFormIssue.LABEL, mandateFormIssue("g", "x".repeat(101), listOf("a.org"), emptyList()))
        assertEquals(MandateFormIssue.NOTHING_ENTRUSTED, mandateFormIssue("g", "x", emptyList(), emptyList()))
        assertEquals(MandateFormIssue.TOO_MANY, mandateFormIssue("g", "x", List(51) { "a$it.org" }, emptyList()))
        assertNull(mandateFormIssue("g", "x".repeat(100), emptyList(), listOf("/a")))
    }

    // ── list ──
    private fun mandate(id: String, state: String, expires: String) =
        MemoryMandate(id = id, profile = "g", label = id, state = state, expiresAt = expires)

    @Test
    fun mandatesAreGroupedActiveFirstAndEndedAfterTheSoonestEndFirst() {
        val a = mandate("late", "active", "2026-10-25T00:00:00+00:00")
        val b = mandate("soon", "active", "2026-10-20T00:00:00+00:00")
        val c = mandate("old", "expired", "2026-10-01T00:00:00+00:00")
        val d = mandate("cut", "revoked", "2026-10-30T00:00:00+00:00")
        val grouped = groupMandates(listOf(a, c, b, d))
        assertEquals(listOf("soon", "late"), grouped.active.map { it.id })
        assertEquals(listOf("old", "cut"), grouped.ended.map { it.id })
    }

    @Test
    fun timeLeftIsSaidPlainly() {
        val now = 1_792_404_000_000L // 2026-10-19T10:00:00Z
        assertEquals("1 j 2 h", mandateTimeLeft("2026-10-20T12:00:00+00:00", now))
        assertEquals("2 h 5 min", mandateTimeLeft("2026-10-19T12:05:00+00:00", now))
        assertEquals("30 min", mandateTimeLeft("2026-10-19T10:30:00+00:00", now))
        assertEquals("moins d'une minute", mandateTimeLeft("2026-10-19T10:00:20+00:00", now))
        assertEquals("terminé", mandateTimeLeft("2026-10-19T09:00:00+00:00", now))
        assertEquals("2 h 5 min", mandateTimeLeft("2026-10-19T12:05:00.123456+00:00", now))
        assertEquals("", mandateTimeLeft("garbage", now))
    }

    // ── errors ──
    @Test
    fun aRefusalKeepsTheServiceWording() {
        val e = IOException("""HTTP 422: {"error":"refused by agent-memory","detail":"too many active mandates for g (20)"}""")
        assertEquals("too many active mandates for g (20)", mandateErrorMessage(e))
    }

    @Test
    fun anUnknownProfileOnCreationIsSaidPlainly() {
        assertEquals("Ce profil n'existe plus ou est révoqué", mandateErrorMessage(IOException("HTTP 404: {}")))
    }
}
