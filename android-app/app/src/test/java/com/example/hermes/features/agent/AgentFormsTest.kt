package com.example.hermes.features.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFormsTest {
    @Test
    fun `a workspace path must be absolute`() {
        assertEquals(WorkspacePathError.Required, workspacePathError("  "))
        assertEquals(WorkspacePathError.NotAbsolute, workspacePathError("workspace/projet"))
        assertNull(workspacePathError("/workspace/projet"))
        assertNull(workspacePathError("  /workspace/projet  "))
    }

    @Test
    fun `a memory section is dirty only when its text changed`() {
        assertFalse(isDirty(original = "a", edited = "a"))
        assertTrue(isDirty(original = "a", edited = "b"))
        assertFalse(isDirty(original = null, edited = ""))
        assertTrue(isDirty(original = null, edited = "x"))
    }

    @Test
    fun `memory tabs map to the server's section names`() {
        assertEquals(listOf("memory", "user", "soul"), MemorySection.entries.map { it.apiName })
        assertEquals(listOf("MEMORY.md", "USER.md", "SOUL.md"), MemorySection.entries.map { it.fileName })
    }
}
