package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class ConfirmationTextTest {

    private fun sms(read: Set<String> = emptySet()) =
        ConfirmationRequest("c1", "sms_send", "Alice", "+33612345678", "Bonjour", read)

    private fun call() = ConfirmationRequest("c2", "call_place", "Alice", "+33612345678", null, emptySet())

    private fun calendar(read: Set<String> = emptySet()) = ConfirmationRequest(
        "c3", "calendar_create", "", "", null, read,
        headline = "Créer cet événement ?", details = "« Dentiste »\n2026-10-07 15:00 → 16:00"
    )

    @Test
    fun anSmsNamesTheRecipientAndShowsTheExactText() {
        assertEquals("Envoyer ce SMS à Alice (+33612345678) ?", ConfirmationText.titleOf(sms()))
        assertEquals("« Bonjour »", ConfirmationText.bodyOf(sms()))
    }

    @Test
    fun aCallNamesTheContactAndHasNoBody() {
        assertEquals("Appeler Alice (+33612345678) ?", ConfirmationText.titleOf(call()))
        assertEquals("", ConfirmationText.bodyOf(call()))
    }

    @Test
    fun aCalendarChangeUsesItsOwnHeadlineAndDetails() {
        assertEquals("Créer cet événement ?", ConfirmationText.titleOf(calendar()))
        assertEquals("« Dentiste »\n2026-10-07 15:00 → 16:00", ConfirmationText.bodyOf(calendar()))
    }

    @Test
    fun theBannerComesAfterTheContentWheneverSomethingExternalWasRead() {
        val body = ConfirmationText.bodyOf(sms(setOf("screen", Consent.SMS_READ)))
        assertEquals("« Bonjour »\n\nProposé après lecture de : écran, SMS", body)
        assertEquals("Proposé après lecture de : SMS", ConfirmationText.bodyOf(call().copy(readKinds = setOf(Consent.SMS_READ))))
        assertTrue(ConfirmationText.bodyOf(calendar(setOf(Consent.CALENDAR))).endsWith("Proposé après lecture de : calendrier"))
    }

    @Test
    fun anUnknownKindStillAsksInPlainWords() {
        val odd = ConfirmationRequest("c4", "something", "", "", null, emptySet())
        assertEquals("Hermes demande votre accord.", ConfirmationText.titleOf(odd))
    }
}
