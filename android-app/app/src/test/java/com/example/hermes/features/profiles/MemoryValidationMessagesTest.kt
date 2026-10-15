package com.example.hermes.features.profiles

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryValidationMessagesTest {

    @Test
    fun aRefusedTokenIsExplained() {
        assertEquals("Jeton propriétaire refusé ou absent", validationErrorMessage(IOException("HTTP 401: {}")))
        assertEquals("Jeton propriétaire refusé ou absent", validationErrorMessage(IOException("HTTP 403: {}")))
    }

    @Test
    fun theServiceStatesHaveTheirOwnMessage() {
        assertEquals("Élément introuvable", validationErrorMessage(IOException("HTTP 404: x")))
        assertEquals("Élément non validable", validationErrorMessage(IOException("HTTP 409: x")))
        assertEquals("Service mémoire non configuré", validationErrorMessage(IOException("HTTP 503: x")))
        assertEquals("Service mémoire injoignable", validationErrorMessage(IOException("HTTP 502: x")))
    }

    @Test
    fun aMissingTokenAsksForIt() {
        assertEquals(
            "Saisis d'abord le jeton propriétaire",
            validationErrorMessage(IOException("Jeton propriétaire non configuré"))
        )
    }

    @Test
    fun theRawResponseIsNeverShown() {
        val shown = validationErrorMessage(IOException("HTTP 500: secret-body-with-token abc"))
        assertFalse(shown.contains("secret-body-with-token"))
        assertEquals("Erreur du service mémoire", shown)
    }
}
