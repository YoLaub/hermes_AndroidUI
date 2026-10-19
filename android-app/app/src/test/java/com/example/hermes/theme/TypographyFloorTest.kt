package com.example.hermes.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The design forbids text under 12 sp, metadata included. */
class TypographyFloorTest {
    @Test
    fun `every theme text style is at least 12 sp`() {
        val styles = with(HermesTypography) {
            listOf(
                displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
                titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
                labelLarge, labelMedium, labelSmall,
            )
        } + CodeStyle
        styles.forEach { assertTrue("${it.fontSize} is under 12 sp", it.fontSize.value >= 12f) }
    }

    @Test
    fun `no screen hard-codes a font size under 12 sp`() {
        val tooSmall = Regex("""fontSize\s*=\s*([0-9]+(\.[0-9]+)?)\.sp""")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    tooSmall.findAll(line).firstOrNull { it.groupValues[1].toDouble() < 12.0 }
                        ?.let { "${file.name}:${i + 1}" }
                }
            }
            .toList()
        assertTrue("Font sizes under 12 sp: $offenders", offenders.isEmpty())
    }
}
