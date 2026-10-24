package com.example.hermes.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An IconButton shrunk with Modifier.size(< 48.dp) is announced to TalkBack with that size,
 * even though Compose widens the finger hit area (HANDOFF step 19: 48 dp targets).
 */
class TouchTargetSizeTest {
    private val iconButton = Regex("""IconButton\((.*?)\)\s*\{""", RegexOption.DOT_MATCHES_ALL)
    private val shrunk = Regex("""modifier\s*=\s*Modifier[^\n]*?\.size\((\d+)\.dp\)""")

    @Test
    fun `no icon button is shrunk below 48 dp`() {
        val offenders = File("src/main/java/com/example/hermes").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val source = file.readText()
                iconButton.findAll(source).mapNotNull { call ->
                    val size = shrunk.find(call.groupValues[1])?.groupValues?.get(1)?.toInt()
                    if (size != null && size < 48) {
                        "${file.name}:${source.take(call.range.first).count { it == '\n' } + 1} ($size dp)"
                    } else null
                }
            }
            .toList()
        assertTrue("Undersized icon buttons: $offenders", offenders.isEmpty())
    }
}
