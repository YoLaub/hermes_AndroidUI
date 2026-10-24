package com.example.hermes.features.agent

import com.example.hermes.core.model.ProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test
    fun `the active and the default profiles cannot be deleted`() {
        assertFalse(canDeleteProfile(ProfileInfo(name = "gaston"), active = "gaston"))
        assertFalse(canDeleteProfile(ProfileInfo(name = "default", isDefault = true), active = "gaston"))
        assertTrue(canDeleteProfile(ProfileInfo(name = "freya"), active = "gaston"))
    }

    @Test
    fun `a profile sharing its name with the default or active one cannot be deleted`() {
        // Deletion goes by name: deleting this twin would hit the default profile.
        val all = listOf(ProfileInfo(name = "default", isDefault = true), ProfileInfo(name = "default"), ProfileInfo(name = "freya"))
        assertFalse(canDeleteProfile(all[1], active = "gaston", all = all))
        assertTrue(canDeleteProfile(all[2], active = "gaston", all = all))
        val twinsOfActive = listOf(ProfileInfo(name = "gaston"), ProfileInfo(name = "gaston"))
        assertFalse(canDeleteProfile(twinsOfActive[1], active = "gaston", all = twinsOfActive))
    }

    @Test
    fun `the subtitle gives the model and the skill count`() {
        assertEquals("gpt-5.6 · 98 compétences", profileSubtitle(ProfileInfo(name = "a", model = "gpt-5.6", skillCount = 98)))
        assertEquals("gpt-5.6 · 1 compétence", profileSubtitle(ProfileInfo(name = "a", model = "gpt-5.6", skillCount = 1)))
        assertEquals("gpt-5.6", profileSubtitle(ProfileInfo(name = "a", model = "gpt-5.6")))
        assertEquals("Modèle par défaut", profileSubtitle(ProfileInfo(name = "a")))
    }

    @Test
    fun `only john may control the phone`() {
        assertTrue(isControlProfile("john"))
        assertFalse(isControlProfile("gaston"))
    }
}
