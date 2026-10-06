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

    // ── The same person saved by several accounts is not ambiguous ──

    @Test
    fun theSameLineSavedInDifferentFormatsIsOneRecipientNotAnAmbiguity() {
        val google = ContactEntry("Marie-Hélène", "06 12 34 56 78")
        val whatsapp = ContactEntry("Marie-Hélène", "+33612345678")
        val r = RecipientPure.resolve("Marie-Hélène", listOf(google, whatsapp))
        assertTrue(r is RecipientPure.Resolution.Found)
    }

    @Test
    fun whenTheSameLineIsStoredTwiceTheInternationalFormIsUsed() {
        val local = ContactEntry("Marie-Hélène", "06 12 34 56 78")
        val intl = ContactEntry("Marie-Hélène", "+33 6 12 34 56 78")
        for (order in listOf(listOf(local, intl), listOf(intl, local))) {
            val found = RecipientPure.resolve("marie-hélène", order) as RecipientPure.Resolution.Found
            assertEquals("+33 6 12 34 56 78", found.contact.number)
        }
    }

    @Test
    fun threeCopiesOfTheSameLineAreStillOneRecipient() {
        val copies = listOf(
            ContactEntry("Marie-Hélène", "0612345678"),
            ContactEntry("Marie-Hélène", "+33612345678"),
            ContactEntry("Marie-Hélène", "06.12.34.56.78")
        )
        assertTrue(RecipientPure.resolve("Marie-Hélène", copies) is RecipientPure.Resolution.Found)
    }

    @Test
    fun twoDifferentLinesUnderTheSameNameStayAmbiguous() {
        val mobile = ContactEntry("Marie-Hélène", "06 12 34 56 78")
        val other = ContactEntry("Marie-Hélène", "07 11 22 33 44")
        assertEquals(RecipientPure.Resolution.Ambiguous, RecipientPure.resolve("Marie-Hélène", listOf(mobile, other, mobile)))
    }

    @Test
    fun aNumberQueryAlsoCollapsesCopiesOfTheSameLine() {
        val copies = listOf(ContactEntry("Marie", "06 12 34 56 78"), ContactEntry("Marie", "+33612345678"))
        assertTrue(RecipientPure.resolve("0612345678", copies) is RecipientPure.Resolution.Found)
    }

    @Test
    fun namesAreComparedAfterUnicodeNormalisation() {
        val composed = "Marie-H\u00e9l\u00e8ne"            // é and è as single characters
        val decomposed = "Marie-He\u0301le\u0300ne"         // e + combining accents
        val r = RecipientPure.resolve(decomposed, listOf(ContactEntry(composed, "0612345678")))
        assertTrue(r is RecipientPure.Resolution.Found)
    }

    @Test
    fun ambiguityNamesTheNumberOfDistinctLinesAndNothingElse() {
        val a = ContactEntry("Bob", "06 98 76 54 32")
        val b = ContactEntry("Bob", "07 11 22 33 44")
        val c = ContactEntry("Bob", "06 11 11 11 11")
        assertEquals(3, RecipientPure.distinctLines("Bob", listOf(a, b, c, a)))
        assertEquals(1, RecipientPure.distinctLines("Bob", listOf(a, ContactEntry("Bob", "+33698765432"))))
    }
}
