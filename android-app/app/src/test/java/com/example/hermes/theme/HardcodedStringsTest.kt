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
    )

    private val literalUiText = Regex(
        """(?:(?<![A-Za-z])Text\(\s*(?:text\s*=\s*)?|contentDescription\s*=\s*|label\s*=\s*\{\s*Text\(\s*|placeholder\s*=\s*\{\s*Text\(\s*|""" +
            """title\s*=\s*|subtitle\s*=\s*|message\s*=\s*|confirmLabel\s*=\s*)"((?:[^"\\]|\\.)*[A-Za-zÀ-ÿ](?:[^"\\]|\\.)*)""""
    )

    private val template = Regex("""\$\{[^}]*\}|\$[A-Za-z_][\w.]*""")

    /** "$count" or "#${id}" only formats values; there is nothing to translate. */
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
}
