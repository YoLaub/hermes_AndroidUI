package com.example.hermes.features.chat

import com.example.hermes.core.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatHeaderTest {
    private val sessions = listOf(
        SessionSummary(sessionId = "a", title = "Plan de migration"),
        SessionSummary(sessionId = "b"),
        SessionSummary(sessionId = "c", title = "  "),
    )

    @Test
    fun `the title is the open session's title`() {
        assertEquals("Plan de migration", chatSessionTitle(sessions, "a"))
    }

    @Test
    fun `no session, an unknown one or a placeholder title gives no title`() {
        assertNull(chatSessionTitle(sessions, null))
        assertNull(chatSessionTitle(sessions, "zzz"))
        assertNull(chatSessionTitle(sessions, "b")) // server default "New Chat"
        assertNull(chatSessionTitle(sessions, "c"))
    }

    @Test
    fun `the subtitle tells whether the agent is answering`() {
        assertEquals(ChatActivity.Ready, chatActivity(isStreaming = false))
        assertEquals(ChatActivity.Answering, chatActivity(isStreaming = true))
    }

    @Test
    fun `profile ids are shown capitalised`() {
        assertEquals("Gaston", profileDisplayName("gaston"))
        assertEquals("Default", profileDisplayName("default"))
    }
}
