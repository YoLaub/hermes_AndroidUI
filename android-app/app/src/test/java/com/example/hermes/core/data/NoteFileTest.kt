package com.example.hermes.core.data

import com.example.hermes.core.model.AgentNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A note is a Markdown file of the workspace that the agents read and write with their usual file tools. */
class NoteFileTest {

    @Test
    fun `a plain Markdown file is a note titled by its name`() {
        val note = parseNote("notes/Idées salon.md", "# Liste\n- stand B12\n")

        assertEquals("notes/Idées salon.md", note.id)
        assertEquals("Idées salon", note.title)
        assertEquals("# Liste\n- stand B12\n", note.content)
        assertFalse(note.isPinned)
        assertTrue(note.tags.isEmpty())
        assertEquals("", note.author)
    }

    @Test
    fun `the header gives pin, tags and author`() {
        val text = "---\npinned: true\ntags: [CRM, Morbihan]\nauthor: gaston\n---\nCorps de la note\n"

        val note = parseNote("notes/CRM.md", text)

        assertTrue(note.isPinned)
        assertEquals(listOf("CRM", "Morbihan"), note.tags)
        assertEquals("gaston", note.author)
        assertEquals("agent", note.authorRole)
        assertEquals("Corps de la note\n", note.content)
    }

    @Test
    fun `a note is written back with its header only when it has one`() {
        assertEquals("Texte", renderNote(AgentNote(title = "t", content = "Texte", author = "")))
        assertEquals(
            "---\npinned: true\ntags: [CRM, Morbihan]\nauthor: Moi\n---\nTexte",
            renderNote(AgentNote(title = "t", content = "Texte", isPinned = true, tags = listOf("CRM", "Morbihan"))),
        )
    }

    @Test
    fun `parse and render round-trip`() {
        val note = AgentNote(title = "CRM", content = "Ligne 1\nLigne 2\n", author = "gaston", isPinned = true, tags = listOf("a"))

        val back = parseNote("notes/CRM.md", renderNote(note))

        assertEquals(listOf(note.content, note.author, note.tags), listOf(back.content, back.author, back.tags))
        assertTrue(back.isPinned)
    }

    @Test
    fun `a title becomes a safe file name`() {
        assertEquals("Objectifs 2027.md", noteFileName("  Objectifs 2027 "))
        assertEquals("a - b - c.md", noteFileName("a/b\\c"))
        assertEquals("Note.md", noteFileName("../"))
        assertEquals(80 + ".md".length, noteFileName("x".repeat(200)).length)
    }
}
