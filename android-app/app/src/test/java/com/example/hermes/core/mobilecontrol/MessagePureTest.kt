package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class MessagePureTest {

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60 * 1000

    // ── One-time codes never reach the model ──

    @Test
    fun codesOfFourToEightDigitsAreMasked() {
        assertEquals("Votre code est [code].", MessagePure.maskCodes("Votre code est 482913."))
        assertEquals("PIN [code]", MessagePure.maskCodes("PIN 1234"))
        assertEquals("code [code] valable", MessagePure.maskCodes("code 12345678 valable"))
    }

    @Test
    fun codesSplitBySpacesOrDashesAreMaskedToo() {
        assertEquals("code [code]", MessagePure.maskCodes("code 123 456"))
        assertEquals("code [code]", MessagePure.maskCodes("code 123-456"))
    }

    @Test
    fun shortAndLongNumbersAreKept() {
        assertEquals("rdv le 12 à 3 h", MessagePure.maskCodes("rdv le 12 à 3 h"))
        assertEquals("tel 0612345678", MessagePure.maskCodes("tel 0612345678"))
        assertEquals("tel 06 12 34 56 78", MessagePure.maskCodes("tel 06 12 34 56 78"))
    }

    @Test
    fun textWithoutDigitsIsUntouched() {
        assertEquals("Bonjour, ça va ?", MessagePure.maskCodes("Bonjour, ça va ?"))
    }

    // ── Window, order, caps ──

    private fun sms(id: Long, ageHours: Double, body: String? = "hello", from: String? = "Alice") =
        RawSms(address = "+3360000000$id", contactName = from, dateMs = now - (ageHours * hour).toLong(), body = body, incoming = true)

    @Test
    fun onlyTheLastTwentyFourHoursAreKeptNewestFirst() {
        val rows = listOf(sms(1, 30.0), sms(2, 23.0), sms(3, 1.0), sms(4, 5.0))
        val out = MessagePure.toMessages(rows, now)
        assertEquals(listOf(now - 1 * hour, now - 5 * hour, now - 23 * hour), out.map { it.dateMs })
    }

    @Test
    fun atMostTwentyMessagesAreKept() {
        val rows = (1..40).map { sms(it.toLong(), it / 10.0) }
        assertEquals(MessagePure.MAX_MESSAGES, MessagePure.toMessages(rows, now).size)
    }

    @Test
    fun bodyIsMaskedAndCutAndSenderIsTheContactNameWhenKnown() {
        val long = "a".repeat(600) + " 482913"
        val m = MessagePure.toMessages(listOf(sms(1, 1.0, body = "code 482913 " + long)), now).single()
        assertFalse(m.text!!.contains("482913"))
        assertTrue(m.text!!.length <= MessagePure.MAX_TEXT_CHARS)
        assertEquals("Alice", m.sender)
    }

    @Test
    fun anUnknownSenderFallsBackToTheNumberAndABlankBodyBecomesNull() {
        val m = MessagePure.toMessages(listOf(sms(1, 1.0, body = "  ", from = null)), now).single()
        assertEquals("+33600000001", m.sender)
        assertNull(m.text)
    }

    // ── Call log ──

    private fun call(ageHours: Double, type: Int, secs: Long = 30, name: String? = null) =
        RawCall(number = "+33612345678", contactName = name, dateMs = now - (ageHours * hour).toLong(), durationSec = secs, androidType = type)

    @Test
    fun callTypesAreMappedAndOldCallsDropped() {
        val rows = listOf(call(1.0, 1), call(2.0, 2), call(3.0, 3), call(4.0, 5), call(30.0, 1))
        assertEquals(listOf("incoming", "outgoing", "missed", "rejected"), MessagePure.toCalls(rows, now).map { it.direction })
    }

    @Test
    fun atMostTwentyCallsAreKept() {
        val rows = (1..40).map { call(it / 10.0, 1) }
        assertEquals(MessagePure.MAX_MESSAGES, MessagePure.toCalls(rows, now).size)
    }

    @Test
    fun anUnknownCallTypeIsLabelledOtherAndNegativeDurationIsZero() {
        val c = MessagePure.toCalls(listOf(call(1.0, 99, secs = -5)), now).single()
        assertEquals("other", c.direction)
        assertEquals(0L, c.durationSec)
    }
}
