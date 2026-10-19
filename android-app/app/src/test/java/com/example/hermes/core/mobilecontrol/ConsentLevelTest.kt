package com.example.hermes.core.mobilecontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsentLevelTest {
    @Test
    fun `reading consents are level 1`() {
        listOf(Consent.SCREENSHOTS, Consent.CALENDAR, Consent.SMS_READ, Consent.CALL_LOG_READ)
            .forEach { assertEquals(it, ConsentLevel.Read, consentLevel(it)) }
    }

    @Test
    fun `calendar writing is level 2, calls and SMS level 3`() {
        assertEquals(ConsentLevel.ConfirmedWrite, consentLevel(Consent.CALENDAR_WRITE))
        assertEquals(ConsentLevel.CallsAndSms, consentLevel(Consent.SMS_SEND))
        assertEquals(ConsentLevel.CallsAndSms, consentLevel(Consent.CALL_PLACE))
    }

    @Test
    fun `every known consent has a level`() {
        assertEquals(Consent.KNOWN, Consent.KNOWN.filter { consentLevel(it) != null }.toSet())
    }
}
