package com.example.hermes.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The action gate goes through the relay with the owner token only, never with the Hermes session cookie. */
class MemoryActionRelayTest {

    private fun capturing(into: MutableList<Request>, body: String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            into.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    private val action = """{"items":[{"id":"1c9eb27b-5212-498a-ad1f-641749940202","kind":"run","profile":"gaston",
        "session_id":"423772f5e8bb","label":"set -o pipefail","reason":"why","requested_at":"2026-10-16T21:00:00Z"}]}"""

    @Test
    fun pendingActionsAreAskedToTheRelayWithTheOwnerTokenOnly() = runBlocking {
        val seen = mutableListOf<Request>()
        val auth = AuthInterceptor().apply { sessionCookie = "hermes-session"; activeProfile = "memtest" }
        val client = HermesApiClient(auth, relayClient = capturing(seen, action))

        val result = client.getMemoryActions("https://relay.example", "tok-owner")

        val item = result.getOrThrow().items.single()
        assertEquals("1c9eb27b-5212-498a-ad1f-641749940202", item.id)
        assertEquals("gaston", item.profile)
        assertEquals("https://relay.example/api/memory/actions", seen.single().url.toString())
        assertEquals("tok-owner", seen.single().header("X-Memory-Owner-Token"))
        assertNull(seen.single().header("Cookie"))
    }

    @Test
    fun anActionDecisionIsPostedToTheRelay() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), relayClient = capturing(seen, """{"ok":true}"""))

        val result = client.decideMemoryAction("https://relay.example", "tok-owner", "abc-1", "approved")

        assertTrue(result.getOrThrow().ok)
        assertEquals("https://relay.example/api/memory/actions/abc-1", seen.single().url.toString())
        assertEquals("POST", seen.single().method)
    }
}
