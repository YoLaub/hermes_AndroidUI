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

/** The owner token goes to the relay only, and never with the Hermes session cookie. */
class MemoryValidationRelayTest {

    private fun capturing(into: MutableList<Request>, body: String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            into.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    @Test
    fun theListIsAskedToTheRelayWithTheOwnerTokenOnly() = runBlocking {
        val seen = mutableListOf<Request>()
        val auth = AuthInterceptor().apply { sessionCookie = "hermes-session"; activeProfile = "memtest" }
        val client = HermesApiClient(auth, relayClient = capturing(seen, """{"items":[]}"""))

        val result = client.getMemoryValidations("https://relay.example", "tok-owner")

        assertTrue(result.isSuccess)
        assertEquals("https://relay.example/api/memory/validations?limit=50", seen.single().url.toString())
        assertEquals("tok-owner", seen.single().header("X-Memory-Owner-Token"))
        assertNull(seen.single().header("Cookie"))
    }

    @Test
    fun aDecisionIsSentToTheRelay() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), relayClient = capturing(seen, """{"ok":true}"""))

        val result = client.decideMemoryValidation("https://relay.example", "tok-owner", "ev-1", "confirmed")

        assertTrue(result.getOrThrow().ok)
        assertEquals("https://relay.example/api/memory/validations/ev-1", seen.single().url.toString())
        assertEquals("POST", seen.single().method)
    }

    @Test
    fun theDefaultRelayClientCarriesNoHermesCredentials() {
        val client = HermesApiClient(AuthInterceptor())
        assertTrue(client.relayClient.interceptors.none { it is AuthInterceptor })
    }
}
