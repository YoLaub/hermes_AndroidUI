package com.example.hermes.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mandates go through the relay with the owner token only, never with the Hermes session cookie. */
class MemoryMandatesRelayTest {

    private fun capturing(into: MutableList<Request>, body: String, code: Int = 200): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            into.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    private fun bodyOf(request: Request): String = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    @Test
    fun mandatesAreListed() = runBlocking {
        val seen = mutableListOf<Request>()
        val auth = AuthInterceptor().apply { sessionCookie = "hermes-session" }
        val body = """{"items":[{"id":"m-1","profile":"gaston","label":"Trip","hosts":["*.site.org"],
            "folders":["/home/g"],"created_at":"2026-10-19T10:00:00+00:00",
            "expires_at":"2026-10-20T10:00:00+00:00","state":"active"}]}"""
        val client = HermesApiClient(auth, relayClient = capturing(seen, body))

        val item = client.getMemoryMandates("https://relay.example", "tok-owner").getOrThrow().items.single()

        assertEquals("m-1", item.id)
        assertEquals(listOf("*.site.org"), item.hosts)
        assertEquals("active", item.state)
        assertEquals("https://relay.example/api/memory/mandates", seen.single().url.toString())
        assertEquals("tok-owner", seen.single().header("X-Memory-Owner-Token"))
        assertNull(seen.single().header("Cookie"))
    }

    @Test
    fun aMandateIsCreatedWithAnOffsetInstant() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), relayClient = capturing(seen, """{"id":"m-9"}""", 201))

        val id = client.createMemoryMandate(
            "https://relay.example", "tok-owner", "gaston", "Trip", listOf("a.org"), emptyList(),
            "2026-10-20T10:00:00+00:00"
        ).getOrThrow().id

        assertEquals("m-9", id)
        assertEquals("POST", seen.single().method)
        assertEquals(
            """{"profile":"gaston","label":"Trip","hosts":["a.org"],"folders":[],"expires_at":"2026-10-20T10:00:00+00:00"}""",
            bodyOf(seen.single())
        )
    }

    @Test
    fun aMandateIsRevoked() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), relayClient = capturing(seen, """{"ok":true}"""))

        client.revokeMemoryMandate("https://relay.example", "tok-owner", "m-1").getOrThrow()

        assertEquals("POST", seen.single().method)
        assertEquals("https://relay.example/api/memory/mandates/m-1/revoke", seen.single().url.toString())
    }
}
