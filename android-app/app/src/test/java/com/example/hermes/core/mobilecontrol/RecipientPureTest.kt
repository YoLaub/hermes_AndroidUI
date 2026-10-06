package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class RecipientPureTest {

    private val alice = ContactEntry("Alice Martin", "+33 6 12 34 56 78")
    private val bob = ContactEntry("Bob", "06 98 76 54 32")
    private val bob2 = ContactEntry("bob", "07 11 22 33 44")
    private val contacts = listOf(alice, bob)

    @Test
    fun aContactIsFoundByExactNameIgnoringCase() {
        assertEquals(RecipientPure.Resolution.Found(alice), RecipientPure.resolve("alice martin", contacts))
        assertEquals(RecipientPure.Resolution.Found(alice), RecipientPure.resolve("  ALICE MARTIN ", contacts))
    }

    @Test
    fun aPartialNameIsNotAMatch() {
        assertEquals(RecipientPure.Resolution.NotFound, RecipientPure.resolve("Alice", contacts))
    }

    @Test
    fun aContactIsFoundByItsNumberWhateverTheFormat() {
        assertEquals(RecipientPure.Resolution.Found(alice), RecipientPure.resolve("+33612345678", contacts))
        assertEquals(RecipientPure.Resolution.Found(alice), RecipientPure.resolve("0612345678", contacts))
        assertEquals(RecipientPure.Resolution.Found(bob), RecipientPure.resolve("+33 6 98-76-54-32", contacts))
    }

    @Test
    fun aNumberOutsideTheContactsIsNotFound() {
        assertEquals(RecipientPure.Resolution.NotFound, RecipientPure.resolve("+33600000000", contacts))
        assertEquals(RecipientPure.Resolution.NotFound, RecipientPure.resolve("3615", contacts))
    }

    @Test
    fun twoContactsWithTheSameNameAreAmbiguousAndNeverGuessed() {
        assertEquals(RecipientPure.Resolution.Ambiguous, RecipientPure.resolve("bob", listOf(bob, bob2)))
    }

    @Test
    fun blankOrDigitlessTextMatchesNothing() {
        assertEquals(RecipientPure.Resolution.NotFound, RecipientPure.resolve("", contacts))
        assertEquals(RecipientPure.Resolution.NotFound, RecipientPure.resolve("   ", contacts))
        assertEquals(RecipientPure.Resolution.NotFound, RecipientPure.resolve("+", contacts))
    }

    @Test
    fun theNumberToDialIsAlwaysTheContactsOwnNotWhatTheAgentTyped() {
        val found = RecipientPure.resolve("0612345678", contacts) as RecipientPure.Resolution.Found
        assertEquals("+33 6 12 34 56 78", found.contact.number)
    }

    // ── What may be sent ──

    @Test
    fun aTextMustBeNonBlankAndAtMostThreeHundredCharacters() {
        assertNull(RecipientPure.textProblem("Bonjour"))
        assertNull(RecipientPure.textProblem("x".repeat(300)))
        assertNotNull(RecipientPure.textProblem("x".repeat(301)))
        assertNotNull(RecipientPure.textProblem("   "))
        assertNotNull(RecipientPure.textProblem(null))
    }
}
