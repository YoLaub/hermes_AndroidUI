package com.example.hermes.core.network

import com.example.hermes.core.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SseEventParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testParseTokenEvent() {
        val data = """{"text": "Hello, world!"}"""
        val payload = json.decodeFromString<TokenPayload>(data)
        assertEquals("Hello, world!", payload.text)
    }

    @Test
    fun testParseReasoningEvent() {
        val data = """{"text": "Agent thinking step 1"}"""
        val payload = json.decodeFromString<ReasoningPayload>(data)
        assertEquals("Agent thinking step 1", payload.text)
    }

    @Test
    fun testParseToolEvents() {
        val startedData = """{"event_type": "tool.started", "name": "read_file", "preview": "read_file path=main.py"}"""
        val started = json.decodeFromString<ToolEventPayload>(startedData)
        assertEquals("tool.started", started.eventType)
        assertEquals("read_file", started.name)

        val completedData = """{"event_type": "tool.completed", "name": "read_file", "duration": 0.32, "is_error": false}"""
        val completed = json.decodeFromString<ToolCompletePayload>(completedData)
        assertEquals("tool.completed", completed.eventType)
        assertEquals("read_file", completed.name)
        assertEquals(0.32, completed.duration ?: 0.0, 0.001)
        assertFalse(completed.isError)
    }

    @Test
    fun testParseApprovalEvent() {
        val data = """{"id": "appr_123", "tool": "execute_command", "command": "rm -rf cache", "description": "Delete build cache"}"""
        val payload = json.decodeFromString<ApprovalPayload>(data)
        assertEquals("appr_123", payload.id)
        assertEquals("execute_command", payload.tool)
        assertEquals("rm -rf cache", payload.command)
        assertEquals("Delete build cache", payload.description)
    }

    @Test
    fun testParseClarifyEvent() {
        val data = """{"question": "Confirm target?", "options": ["Release", "Debug"]}"""
        val payload = json.decodeFromString<ClarifyPayload>(data)
        assertEquals("Confirm target?", payload.question)
        assertEquals(2, payload.options.size)
        assertEquals("Release", payload.options[0])
    }

    @Test
    fun testParseDoneEvent() {
        val data = """
            {
              "session": {
                "session_id": "sid_done",
                "title": "Turn Completed",
                "messages": [
                  {"role": "user", "content": "Hello"},
                  {"role": "assistant", "content": "Hi there!"}
                ]
              }
            }
        """.trimIndent()
        val payload = json.decodeFromString<SessionDetailResponse>(data)
        assertEquals("sid_done", payload.session.sessionId)
        assertEquals(2, payload.session.messages.size)
    }
}
