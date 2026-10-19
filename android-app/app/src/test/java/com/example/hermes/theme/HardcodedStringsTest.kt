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
        "features/profiles/OpenBaoVaultScreen.kt",
    )

    private val literalUiText = Regex(
        """(?:Text\(\s*(?:text\s*=\s*)?|contentDescription\s*=\s*|label\s*=\s*\{\s*Text\(\s*|placeholder\s*=\s*\{\s*Text\(\s*|""" +
            """title\s*=\s*|subtitle\s*=\s*|message\s*=\s*|confirmLabel\s*=\s*)"((?:[^"\\]|\\.)*[A-Za-zÀ-ÿ](?:[^"\\]|\\.)*)""""
    )

    @Test
    fun `extracted screens have no literal UI text`() {
        val offenders = extracted.flatMap { path ->
            val file = File("src/main/java/com/example/hermes/$path")
            val source = file.readText()
            literalUiText.findAll(source).map { match ->
                "${file.name}:${source.take(match.range.first).count { it == '\n' } + 1} « ${match.groupValues[1]} »"
            }.toList()
        }
        assertTrue("Literal UI text: $offenders", offenders.isEmpty())
    }
}
