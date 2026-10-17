package com.example.hermes.features.profiles

import com.example.hermes.core.model.MemoryProfileCreated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MemoryProfilesLogicTest {

    @Test
    fun secretsAndFinanceAreNotTogglesTheOtherSixAre() {
        assertEquals(listOf("secrets", "finance"), ADMIN_ONLY_ZONES)
        assertEquals(listOf("health", "legal", "personal", "professional", "git", "other"), TOGGLE_ZONES)
    }

    @Test
    fun anAdminOnlyZoneAlreadyHeldInThatListIsSentBackUnchanged() {
        val sent = grantsToSend(held = listOf("secrets", "other"), selected = setOf("other", "git"))
        assertEquals(listOf("secrets", "git", "other"), sent)
    }

    @Test
    fun anAdminOnlyZoneIsNeverAdded() {
        assertEquals(listOf("other"), grantsToSend(held = emptyList(), selected = setOf("other", "secrets", "finance")))
    }

    @Test
    fun readStaysReadAndWriteStaysWrite() {
        val read = grantsToSend(held = listOf("finance"), selected = emptySet())
        val write = grantsToSend(held = emptyList(), selected = emptySet())
        assertEquals(listOf("finance"), read)
        assertEquals(emptyList<String>(), write)
    }

    @Test
    fun aProfileWithNoGrantAtAllIsValid() {
        assertEquals(emptyList<String>(), grantsToSend(emptyList(), emptySet()))
    }

    @Test
    fun theSentListFollowsTheSensitivityOrderWhateverTheSelectionOrder() {
        assertEquals(listOf("health", "git", "other"), grantsToSend(emptyList(), linkedSetOf("other", "git", "health")))
    }

    @Test
    fun profileNamesAreLowercaseLettersDigitsDashAndUnderscore() {
        assertTrue(isValidProfileName("gaston"))
        assertTrue(isValidProfileName("mem-test_2"))
        assertFalse(isValidProfileName(""))
        assertFalse(isValidProfileName("Gaston"))
        assertFalse(isValidProfileName("a b"))
        assertFalse(isValidProfileName("../x"))
        assertFalse(isValidProfileName("é"))
        assertFalse(isValidProfileName("a".repeat(101)))
        assertTrue(isValidProfileName("a".repeat(100)))
    }

    @Test
    fun theServiceWordingOfARefusalIsShownAsIs() {
        val detail = "editing grants through the API is off on this service: use the admin command"
        val error = IOException("""HTTP 403: {"error":"refused by agent-memory","detail":"$detail"}""")
        assertEquals(detail, profileErrorMessage(error))
    }

    @Test
    fun withoutAServiceWordingTheUsualMessagesApply() {
        assertEquals("Jeton propriétaire refusé ou absent", profileErrorMessage(IOException("HTTP 401: {}")))
        assertEquals("Erreur réseau", profileErrorMessage(IOException("timeout")))
    }

    @Test
    fun theOneTimeTokenNeverAppearsWhenTheObjectIsPrinted() {
        val created = MemoryProfileCreated(name = "gaston", token = "super-secret-token")
        assertFalse(created.toString().contains("super-secret-token"))
        assertTrue(created.toString().contains("gaston"))
    }
}
