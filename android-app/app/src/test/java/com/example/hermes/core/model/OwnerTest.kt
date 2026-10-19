package com.example.hermes.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerTest {
    @Test
    fun `the owner is Moi in new data`() {
        assertTrue(isOwnerName(OWNER_NAME))
    }

    @Test
    fun `older workspace data still reads as the owner's`() {
        assertTrue(isOwnerName("Yoann"))
        assertTrue(isOwnerName("yoann"))
    }

    @Test
    fun `agents are not the owner`() {
        assertFalse(isOwnerName("Gaston"))
        assertFalse(isOwnerName(""))
    }

    @Test
    fun `a filter on the owner matches old and new owner names`() {
        assertTrue(matchesPerson("Yoann", OWNER_NAME))
        assertTrue(matchesPerson("gaston", "Gaston"))
        assertFalse(matchesPerson("Gaston", OWNER_NAME))
    }
}
