package com.example.hermes.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class DocumentsApiTest {
    @Test fun documentRequestsPinProfileAndEncodePathsWithoutASession() = runBlocking {
        var seen: Request? = null
        val auth = AuthInterceptor().apply { activeProfile = "mario"; sessionCookie = "session" }
        val client = OkHttpClient.Builder().addInterceptor(auth).addInterceptor { chain ->
            seen = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .addHeader("Set-Cookie", "hermes_profile=john")
                .body("""{"content":"hello","revision":"v1"}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = DocumentsApi(client)
        val doc = api.read("https://hermes.example", "john", "space", "Mes idées/a b.md")
        assertEquals("hello", doc.content)
        assertEquals("Mes idées/a b.md", seen!!.url.queryParameter("path"))
        assertNull(seen!!.url.queryParameter("session_id"))
        assertTrue(seen!!.header("Cookie")!!.contains("hermes_profile=john"))
        assertEquals("mario", auth.activeProfile)
    }

    @Test fun downloadStreamsTheOriginalBinaryBytes() = runBlocking {
        val data = byteArrayOf(0, 1, -1, 32)
        val client = OkHttpClient.Builder().addInterceptor { c ->
            Response.Builder().request(c.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(data.toResponseBody("application/pdf".toMediaType())).build()
        }.build()
        val output = ByteArrayOutputStream()
        DocumentsApi(client).download("https://h.example", "john", "space", "test.pdf", output)
        assertArrayEquals(data, output.toByteArray())
    }
}
