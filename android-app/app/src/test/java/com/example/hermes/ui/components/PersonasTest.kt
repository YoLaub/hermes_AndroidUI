package com.example.hermes.ui.components

import com.example.hermes.core.model.ProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonasTest {
    @Test
    fun `personas come from the server profiles, in their order`() {
        val personas = personasOf(listOf(ProfileInfo(name = "gaston", model = "gpt-5.6"), ProfileInfo(name = "freya")))
        assertEquals(listOf("Gaston", "Freya"), personas.map { it.name })
        assertEquals(listOf("gaston", "freya"), personas.map { it.id })
        assertEquals("gpt-5.6", personas[0].model)
    }

    @Test
    fun `initials are the first two letters, or the first letter of two words`() {
        assertEquals("GA", initialsOf("Gaston"))
        assertEquals("JD", initialsOf("jean dupont"))
        assertEquals("JD", initialsOf("jean-dupont"))
        assertEquals("X", initialsOf("x"))
        assertEquals("?", initialsOf("  "))
    }

    @Test
    fun `an unknown persona still gets a name and initials`() {
        val p = personaOrFallback(emptyList(), "mario")
        assertEquals("Mario", p.name)
        assertEquals("MA", p.initials)
    }
}
