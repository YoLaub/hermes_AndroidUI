package com.example.hermes.core.mobilecontrol

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PairingClientTest {

    private class FakeTransport(
        private val reply: HttpReply? = null,
        private val failure: Exception? = null
    ) : PairingTransport {
        var calls = 0
        var lastUrl: String? = null
        var lastBody: String? = null
        var lastHeaders: Map<String, String> = emptyMap()

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>): HttpReply {
            calls++
            lastUrl = url
            lastBody = body
            lastHeaders = headers
            failure?.let { throw it }
            return reply!!
        }
    }

    private fun pair(
        transport: FakeTransport,
        relayUrl: String = "https://relay.example",
        code: String = " a1b2c3 ",
        current: String? = null
    ) = runBlocking {
        PairingClient(transport).pair(relayUrl, code, "dev_abcd1234", "Pixel", current)
    }

    private val okBody = """{"ok":true,"device_token":"tok_SECRET_FROM_RELAY_123"}"""

    @Test
    fun successReturnsTheTokenIssuedByTheRelay() {
        val t = FakeTransport(HttpReply(200, okBody))
        val r = pair(t)
        assertTrue(r is PairingResult.Success)
        assertEquals("tok_SECRET_FROM_RELAY_123", (r as PairingResult.Success).deviceToken)
    }

    @Test
    fun requestGoesToVerifyWithTrimmedCodeAndDeviceIdentity() {
        val t = FakeTransport(HttpReply(200, okBody))
        pair(t, relayUrl = "https://relay.example/")
        assertEquals("https://relay.example/api/pair/verify", t.lastUrl)
        val body = Json.parseToJsonElement(t.lastBody!!).jsonObject
        assertEquals("a1b2c3", body["code"]!!.jsonPrimitive.content)
        assertEquals("dev_abcd1234", body["device_id"]!!.jsonPrimitive.content)
        assertEquals("Pixel", body["device_name"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("current_device_token"))
    }

    @Test
    fun anExistingDeviceTokenIsSentSoARePairIsAuthorised() {
        val t = FakeTransport(HttpReply(200, okBody))
        pair(t, current = "tok_old")
        val body = Json.parseToJsonElement(t.lastBody!!).jsonObject
        assertEquals("tok_old", body["current_device_token"]!!.jsonPrimitive.content)
    }

    @Test
    fun blankCodeNeverReachesTheNetwork() {
        val t = FakeTransport(HttpReply(200, okBody))
        val r = pair(t, code = "   ")
        assertEquals(PairingFailure.INVALID_CODE, (r as PairingResult.Failure).reason)
        assertEquals(0, t.calls)
    }

    @Test
    fun nonHttpRelayUrlIsRejectedBeforeAnyRequest() {
        val t = FakeTransport(HttpReply(200, okBody))
        val r = pair(t, relayUrl = "ftp://relay.example")
        assertEquals(PairingFailure.BAD_RELAY_URL, (r as PairingResult.Failure).reason)
        assertEquals(0, t.calls)
    }

    @Test
    fun relayErrorsMapToDistinctReasons() {
        val cases = mapOf(
            400 to PairingFailure.INVALID_CODE,
            403 to PairingFailure.ALREADY_REGISTERED,
            409 to PairingFailure.REGISTRATION_CONFLICT,
            429 to PairingFailure.RATE_LIMITED,
            500 to PairingFailure.SERVER_ERROR,
            502 to PairingFailure.SERVER_ERROR
        )
        for ((status, expected) in cases) {
            val r = pair(FakeTransport(HttpReply(status, """{"detail":"x"}""")))
            assertEquals("HTTP $status", expected, (r as PairingResult.Failure).reason)
        }
    }

    @Test
    fun unreachableRelayIsReportedAsSuch() {
        val r = pair(FakeTransport(failure = IOException("Unable to resolve host")))
        assertEquals(PairingFailure.UNREACHABLE, (r as PairingResult.Failure).reason)
    }

    @Test
    fun a200WithoutATokenIsABadResponseNotASuccess() {
        for (body in listOf("""{"ok":true}""", """{"ok":false,"device_token":"tok_x"}""", "not json", "")) {
            val r = pair(FakeTransport(HttpReply(200, body)))
            assertEquals(body, PairingFailure.BAD_RESPONSE, (r as PairingResult.Failure).reason)
        }
    }

    @Test
    fun failureMessagesNeverEchoTheCodeOrAnyToken() {
        val statuses = listOf(400, 403, 409, 429, 500)
        for (status in statuses) {
            val r = pair(
                FakeTransport(HttpReply(status, """{"detail":"code A1B2C3 tok_SECRET_FROM_RELAY_123"}""")),
                code = "A1B2C3", current = "tok_old"
            ) as PairingResult.Failure
            for (secret in listOf("A1B2C3", "tok_SECRET_FROM_RELAY_123", "tok_old")) {
                assertFalse("HTTP $status leaked $secret", r.message.contains(secret, ignoreCase = true))
            }
        }
    }
}
