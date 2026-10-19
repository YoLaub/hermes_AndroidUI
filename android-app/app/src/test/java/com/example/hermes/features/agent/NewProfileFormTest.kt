package com.example.hermes.features.agent

import com.example.hermes.core.model.ProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewProfileFormTest {
    private val existing = listOf(ProfileInfo(name = "gaston"), ProfileInfo(name = "default", isDefault = true))

    @Test
    fun `a profile id is lower case letters, digits, dash and underscore`() {
        assertNull(profileNameError("redacteur-2", existing))
        assertEquals(ProfileNameError.Required, profileNameError("", existing))
        assertEquals(ProfileNameError.Format, profileNameError("-dash", existing))
        assertEquals(ProfileNameError.Format, profileNameError("Avec Espace", existing))
        assertEquals(ProfileNameError.Format, profileNameError("x".repeat(65), existing))
    }

    @Test
    fun `an existing id is refused`() {
        assertEquals(ProfileNameError.Taken, profileNameError("gaston", existing))
    }

    @Test
    fun `the default profile is the starting point`() {
        assertEquals("default", defaultCloneSource(existing))
        assertEquals("gaston", defaultCloneSource(listOf(ProfileInfo(name = "gaston"))))
        assertNull(defaultCloneSource(emptyList()))
    }
}
