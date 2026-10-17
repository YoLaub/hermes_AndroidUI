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

/** Profiles and grants go through the relay with the owner token only, never with the Hermes session cookie. */
class MemoryProfilesRelayTest {

    private fun capturing(into: MutableList<Request>, body: String, code: Int = 200): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            into.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    private fun bodyOf(request: Request): String = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    @Test
    fun profilesAreListed() = runBlocking {
        val seen = mutableListOf<Request>()
        val auth = AuthInterceptor().apply { sessionCookie = "hermes-session" }
        val body = """{"items":[{"name":"gaston","state":"active","read":["other"],"write":[]}]}"""
        val client = HermesApiClient(auth, relayClient = capturing(seen, body))

        val items = client.getMemoryProfiles("https://relay.example", "tok-owner").getOrThrow().items

        assertEquals("gaston", items.single().name)
        assertEquals(listOf("other"), items.single().read)
        assertEquals("https://relay.example/api/memory/profiles", seen.single().url.toString())
        assertEquals("tok-owner", seen.single().header("X-Memory-Owner-Token"))
        assertNull(seen.single().header("Cookie"))
    }

    @Test
    fun aProfileIsCreatedAndItsTokenIsReturned() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), relayClient = capturing(seen, """{"name":"gaston","token":"t-1"}""", 201))

        val created = client.createMemoryProfile("https://relay.example", "tok-owner", "gaston", listOf("other"), emptyList()).getOrThrow()

        assertEquals("t-1", created.token)
        assertEquals("POST", seen.single().method)
        assertEquals("""{"name":"gaston","read":["other"],"write":[]}""", bodyOf(seen.single()))
    }

    @Test
    fun grantsAreReplacedWithAPut() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), relayClient = capturing(seen, """{"ok":true}"""))

        client.setMemoryProfileGrants("https://relay.example", "tok-owner", "gaston", listOf("git"), listOf("other")).getOrThrow()

        assertEquals("PUT", seen.single().method)
        assertEquals("https://relay.example/api/memory/profiles/gaston/grants", seen.single().url.toString())
        assertEquals("""{"read":["git"],"write":["other"]}""", bodyOf(seen.single()))
    }

    @Test
    fun aProfileIsRevoked() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), relayClient = capturing(seen, """{"ok":true}"""))

        client.revokeMemoryProfile("https://relay.example", "tok-owner", "gaston").getOrThrow()

        assertEquals("POST", seen.single().method)
        assertEquals("https://relay.example/api/memory/profiles/gaston/revoke", seen.single().url.toString())
    }
}
