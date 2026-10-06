package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class ExternalReadsTest {
    @Test
    fun operationsThatReturnExternalContentAreRecordedUnderTheirKind() {
        for (op in listOf("observe", "click_element", "scroll", "set_text", "back", "launch_app", "tap_xy")) {
            assertEquals(op, "screen", ExternalReads.kindFor(op))
        }
        assertEquals(Consent.SCREENSHOTS, ExternalReads.kindFor("screenshot"))
        assertEquals(Consent.CALENDAR, ExternalReads.kindFor("calendar_read"))
        assertEquals(Consent.SMS_READ, ExternalReads.kindFor("sms_read"))
        assertEquals(Consent.CALL_LOG_READ, ExternalReads.kindFor("call_log_read"))
    }

    @Test
    fun actionsAndEndOfSessionReadNothing() {
        for (op in listOf("sms_send", "call_place", "end_session")) assertNull(op, ExternalReads.kindFor(op))
    }

    @Test
    fun theTrackerKeepsWhatWasReadAndForgetsOnReset() {
        val reads = ExternalReads()
        reads.record("observe"); reads.record("sms_read"); reads.record("sms_send")
        assertEquals(setOf("screen", Consent.SMS_READ), reads.kinds)
        reads.reset()
        assertTrue(reads.kinds.isEmpty())
    }

    @Test
    fun calendarChangesReadNothingExternal() {
        for (op in listOf("calendar_create", "calendar_update", "calendar_delete")) assertNull(op, ExternalReads.kindFor(op))
    }
}
