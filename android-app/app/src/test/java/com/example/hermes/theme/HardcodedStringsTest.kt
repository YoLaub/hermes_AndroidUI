package com.example.hermes.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Screens whose labels moved to strings.xml must not get literal UI text back
 * (Text, contentDescription, label, placeholder, title, subtitle, message, confirmLabel).
 */
class HardcodedStringsTest {
    private val extracted = listOf(
        "features/workspace/calendar/components/AgendaItemCard.kt",
        "features/workspace/calendar/components/MonthCalendarView.kt",
        "features/workspace/calendar/components/EventEditorDialog.kt",
        "features/workspace/calendar/CalendarScreen.kt",
        "features/workspace/notes/components/DictaphoneModal.kt",
        "features/workspace/notes/components/NoteEditorDialog.kt",
        "features/workspace/notes/components/NoteCard.kt",
        "features/workspace/notes/NotesScreen.kt",
        "features/inbox/InboxScreen.kt",
        "features/auth/AuthScreen.kt",
        "features/agent/WorkspacesScreen.kt",
        "features/agent/AgentScreen.kt",
        "features/agent/NewProfileScreen.kt",
        "features/guard/NewMandateScreen.kt",
        "features/chat/components/ReasoningCard.kt",
        "features/chat/components/ClarifyCard.kt",
        "features/chat/components/MessageBubble.kt",
        "features/chat/components/ApprovalCard.kt",
        "features/chat/components/ChatInputField.kt",
        "features/chat/components/ToolCallCard.kt",
        "features/sessions/SessionsDrawer.kt",
        "features/kanban/components/CreateTaskDialog.kt",
        "features/kanban/components/KanbanTaskCard.kt",
        "features/kanban/components/KanbanTaskDetailScreen.kt",
        "features/kanban/KanbanScreen.kt",
        "features/mobilecontrol/MobileControlScreen.kt",
        "features/profiles/MemoryProfileParts.kt",
        "features/profiles/ProfileEnvScreen.kt",
        "features/profiles/OpenBaoVaultScreen.kt",
        "features/workspace/forum/ForumScreen.kt",
        "features/workspace/forum/components/ForumInputBar.kt",
        "features/workspace/forum/components/ForumMessageItem.kt",
        "features/workspace/forum/components/CreateRoomDialog.kt",
    )

    private val literalUiText = Regex(
        """(?:(?<![A-Za-z])Text\(\s*(?:text\s*=\s*)?|contentDescription\s*=\s*|label\s*=\s*\{\s*Text\(\s*|placeholder\s*=\s*\{\s*Text\(\s*|""" +
            """title\s*=\s*|subtitle\s*=\s*|message\s*=\s*|confirmLabel\s*=\s*)"((?:[^"\\]|\\.)*[A-Za-zÀ-ÿ](?:[^"\\]|\\.)*)""""
    )

    private val template = Regex("""\$\{[^}]*\}|\$[A-Za-z_][\w.]*|%[-0-9.$]*[a-zA-Z]""")

    /** "$count", "#${id}" or "%02d:%02d" only format values; there is nothing to translate. */
    private fun hasWords(literal: String) = template.replace(literal, "").any { it.isLetter() }

    @Test
    fun `extracted screens have no literal UI text`() {
        val offenders = extracted.flatMap { path ->
            val file = File("src/main/java/com/example/hermes/$path")
            val source = file.readText()
            literalUiText.findAll(source).filter { hasWords(it.groupValues[1]) }.map { match ->
                "${file.name}:${source.take(match.range.first).count { it == '\n' } + 1} « ${match.groupValues[1]} »"
            }.toList()
        }
        assertTrue("Literal UI text: $offenders", offenders.isEmpty())
    }

    /** A branch value such as `if (on) "Autorisé" else "Refusé"` or `X -> "Terminé"`. */
    private val branchLiteral = Regex("""(?:if\s*\([^\n]*?\)\s*|else\s+|->\s*)"((?:[^"\\\n]|\\.)*)"""")
    private val constantLike = Regex("""[A-Z0-9_]*""")

    /** Text a person reads: a capital, an accent or a space, and not a CONSTANT_NAME. */
    private fun looksDisplayed(literal: String): Boolean {
        val text = template.replace(literal, "")
        return hasWords(literal) && !constantLike.matches(text.trim()) &&
            (text.any { it.isUpperCase() || it.code > 127 } || text.trim().contains(' '))
    }

    @Test
    fun `extracted screens have no literal UI text in branches`() {
        val offenders = extracted.flatMap { path ->
            val file = File("src/main/java/com/example/hermes/$path")
            file.readLines().flatMapIndexed { i, line ->
                branchLiteral.findAll(line).map { it.groupValues[1] }.filter(::looksDisplayed)
                    .map { "${file.name}:${i + 1} « $it »" }.toList()
            }
        }
        assertTrue("Literal UI text in branches: $offenders", offenders.isEmpty())
    }
}
