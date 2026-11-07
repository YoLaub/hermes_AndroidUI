package com.example.hermes.features.chat

import com.example.hermes.core.model.ChatMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolHistoryTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // Raw OpenAI-style history as stored by the WebUI server.
    private val raw = """
        [
          {"role":"user","content":"Lis le fichier"},
          {"role":"assistant","content":"Je lis.","tool_calls":[
            {"id":"call_1","type":"function","function":{"name":"read_file","arguments":"{\"path\":\"workspace/test.eml\"}"}},
            {"id":"call_2","type":"function","function":{"name":"terminal","arguments":"{}"}}
          ]},
          {"role":"tool","tool_call_id":"call_1","content":"{\"content\": \"1|From: Plomberie\"}"},
          {"role":"assistant","content":"Voici le résumé."}
        ]
    """.trimIndent()

    private fun history() = normalizeHistory(json.decodeFromString<List<ChatMessage>>(raw))

    @Test
    fun `tool result messages are not shown as chat bubbles`() {
        assertEquals(listOf("user", "assistant", "assistant"), history().map { it.role })
    }

    @Test
    fun `tool call name and output come from the OpenAI shape`() {
        val tc = history()[1].toolCalls!![0]
        assertEquals("read_file", tc.name)
        assertEquals("{\"content\": \"1|From: Plomberie\"}", tc.output)
        assertNotNull(tc.args)
    }

    @Test
    fun `a past tool call without result is finished, never spinning`() {
        val tc = history()[1].toolCalls!![1]
        assertEquals("terminal", tc.name)
        assertNotNull(tc.output)
    }

    @Test
    fun `flat name and args shape still works`() {
        val flat = json.decodeFromString<List<ChatMessage>>(
            """[{"role":"assistant","content":"x","tool_calls":[{"id":"c","name":"read_file","args":{"path":"a"},"output":"ok"}]}]"""
        )
        val tc = normalizeHistory(flat)[0].toolCalls!![0]
        assertEquals("read_file", tc.name)
        assertEquals("ok", tc.output)
        assertTrue(tc.args.toString().contains("path"))
    }

    @Test
    fun `user messages are shown without the attached-files suffix`() {
        val msgs = json.decodeFromString<List<ChatMessage>>(
            """[{"role":"user","content":"Résume\n\n[Attached files: /p/a.pdf]"}]"""
        )
        assertEquals("Résume", normalizeHistory(msgs)[0].content)
    }
}
