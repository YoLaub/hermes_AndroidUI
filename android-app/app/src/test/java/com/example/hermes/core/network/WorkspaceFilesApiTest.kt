package com.example.hermes.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Notes are files of a session's workspace, read and written through the WebUI file routes. */
class WorkspaceFilesApiTest {

    private fun capturing(into: MutableList<Request>, body: String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            into.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    private fun fields(request: Request): Map<String, String> {
        val text = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        return Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    @Test
    fun aFolderWithSpacesIsListed() = runBlocking {
        val seen = mutableListOf<Request>()
        val body = """{"entries":[{"name":"Mes idées.md","path":"notes/Mes idées.md","type":"file","size":12},
            {"name":"audio","path":"notes/audio","type":"dir","size":null}],"path":"notes"}"""
        val client = HermesApiClient(AuthInterceptor(), okHttpClient = capturing(seen, body))

        val entries = client.listFiles("https://h.example", "s1", "notes").getOrThrow().entries

        assertEquals("s1", seen.single().url.queryParameter("session_id"))
        assertEquals("notes", seen.single().url.queryParameter("path"))
        assertEquals(listOf("file", "dir"), entries.map { it.type })
    }

    @Test
    fun aFileIsRead() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), okHttpClient = capturing(seen, """{"path":"notes/a b.md","content":"# Bonjour","size":9,"lines":1}"""))

        val content = client.readFile("https://h.example", "s1", "notes/a b.md").getOrThrow().content

        assertEquals("# Bonjour", content)
        assertEquals("/api/file", seen.single().url.encodedPath)
        assertEquals("notes/a b.md", seen.single().url.queryParameter("path"))
    }

    @Test
    fun filesAreCreatedSavedRenamedAndDeleted() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = HermesApiClient(AuthInterceptor(), okHttpClient = capturing(seen, """{"ok":true}"""))

        client.createFile("https://h.example", "s1", "notes/n.md", "x").getOrThrow()
        client.saveFile("https://h.example", "s1", "notes/n.md", "y").getOrThrow()
        client.renameFile("https://h.example", "s1", "notes/n.md", "m.md").getOrThrow()
        client.deleteFile("https://h.example", "s1", "notes/m.md").getOrThrow()

        assertEquals(
            listOf("/api/file/create", "/api/file/save", "/api/file/rename", "/api/file/delete"),
            seen.map { it.url.encodedPath },
        )
        assertEquals(mapOf("session_id" to "s1", "path" to "notes/n.md", "content" to "x"), fields(seen[0]))
        assertEquals(mapOf("session_id" to "s1", "path" to "notes/n.md", "content" to "y"), fields(seen[1]))
        assertEquals(mapOf("session_id" to "s1", "path" to "notes/n.md", "new_name" to "m.md"), fields(seen[2]))
        assertEquals(mapOf("session_id" to "s1", "path" to "notes/m.md"), fields(seen[3]))
    }
}
