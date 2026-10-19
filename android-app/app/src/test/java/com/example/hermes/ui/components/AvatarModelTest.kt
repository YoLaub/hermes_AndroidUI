package com.example.hermes.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarModelTest {
    @Test
    fun `initials are one or two letters or digits, upper case`() {
        assertEquals("GA", sanitizeInitials("ga"))
        assertEquals("J2", sanitizeInitials("j2x"))
        assertEquals("É", sanitizeInitials("é!"))
        assertEquals("", sanitizeInitials(" -"))
    }

    @Test
    fun `the default colour is stable for a profile and in the palette`() {
        val a = defaultColorIndex("gaston")
        assertEquals(a, defaultColorIndex("gaston"))
        assertTrue(a in 0 until AVATAR_PALETTE_SIZE)
        assertNotEquals(defaultColorIndex("gaston"), defaultColorIndex("freya"))
    }

    @Test
    fun `an image wins, else custom initials and colour, else the defaults`() {
        val persona = Persona(id = "gaston", name = "Gaston", initials = "GA")
        assertEquals(AvatarLook.Image("gaston.png"), avatarLook(persona, AvatarSpec(image = "gaston.png"), imageExists = true))
        assertEquals(AvatarLook.Initials("GT", 3), avatarLook(persona, AvatarSpec(image = "gaston.png", initials = "GT", color = 3), imageExists = false))
        assertEquals(AvatarLook.Initials("GA", defaultColorIndex("gaston")), avatarLook(persona, null, imageExists = false))
        assertEquals(AvatarLook.Initials("GA", 5), avatarLook(persona, AvatarSpec(initials = "", color = 5), imageExists = false))
    }

    @Test
    fun `the crop is the centred square of the picture`() {
        assertEquals(CropSquare(left = 100, top = 0, size = 300), centeredSquare(width = 500, height = 300))
        assertEquals(CropSquare(left = 0, top = 50, size = 200), centeredSquare(width = 200, height = 300))
    }
}
