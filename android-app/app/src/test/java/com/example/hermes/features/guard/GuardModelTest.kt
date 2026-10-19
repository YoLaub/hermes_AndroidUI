package com.example.hermes.features.guard

import com.example.hermes.core.model.MemoryProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardModelTest {
    @Test
    fun `the grid lists only the toggleable zones, in sensitivity order`() {
        val rows = zoneGrid(read = setOf("legal", "git"), write = setOf("git"))
        assertEquals(listOf("health", "legal", "personal", "professional", "git", "other"), rows.map { it.zone })
        assertEquals(ZoneRow("git", read = true, write = true), rows.first { it.zone == "git" })
        assertEquals(ZoneRow("health", read = false, write = false), rows.first())
    }

    @Test
    fun `secrets and finance only show as held, never as switches`() {
        val p = MemoryProfile(name = "gaston", read = listOf("secrets", "legal"), write = listOf("finance"))
        assertEquals(listOf("secrets", "finance"), terminalOnlyZones(p))
        assertTrue(zoneGrid(p.read.toSet(), p.write.toSet()).none { it.zone == "secrets" || it.zone == "finance" })
        assertEquals(emptyList<String>(), terminalOnlyZones(MemoryProfile(name = "x")))
    }

    @Test
    fun `tabs come in a fixed order with their counts`() {
        assertEquals(listOf(GuardTab.Pending, GuardTab.Actions, GuardTab.Profiles, GuardTab.Mandates), GuardTab.entries.toList())
    }
}
