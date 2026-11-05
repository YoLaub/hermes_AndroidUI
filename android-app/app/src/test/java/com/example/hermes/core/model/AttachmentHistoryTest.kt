package com.example.hermes.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentHistoryTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private fun attachmentsOf(raw: String): List<ChatAttachment>? =
        json.decodeFromString<ChatMessage>("""{"role":"user","content":"x","attachments":$raw}""").attachments

    @Test
    fun `saved history lists attachments as plain file names`() {
        val list = attachmentsOf("""["logs.txt","photo.png"]""")!!
        assertEquals(listOf("logs.txt", "photo.png"), list.map { it.filename })
    }

    @Test
    fun `server objects use name instead of filename`() {
        val list = attachmentsOf("""[{"name":"a.pdf","path":"/p/a.pdf","mime":"application/pdf","size":12}]""")!!
        assertEquals("a.pdf", list[0].filename)
        assertEquals("/p/a.pdf", list[0].path)
        assertEquals(12L, list[0].size)
    }

    @Test
    fun `the app's own object shape still decodes`() {
        val list = attachmentsOf("""[{"filename":"a.txt","path":"/p/a.txt","is_image":true}]""")!!
        assertEquals("a.txt", list[0].filename)
        assertEquals(true, list[0].isImage)
    }

    @Test
    fun `missing or null attachments stay null`() {
        assertNull(attachmentsOf("null"))
        assertNull(json.decodeFromString<ChatMessage>("""{"role":"user","content":"x"}""").attachments)
    }
}
