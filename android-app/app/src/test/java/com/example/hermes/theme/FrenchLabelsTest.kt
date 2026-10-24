package com.example.hermes.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The labels the redesign renamed (HANDOFF §7) must not come back in the UI code. */
class FrenchLabelsTest {
    private val retired = listOf(
        "Emulator (10.0.2.2)", "LAN IP", "Tips for local deployment", "Probing Hermes instance",
        "Connect to your self-hosted", "How can Hermes help you today?", "Message Hermes",
        "Thought process", "Thinking...", "Reasoning...", "Executing tool...", "Generating response...",
        "Approval Required", "COMMANDES DISPONIBLES", "Dispatch", "Retour au Chat", "Paramètres & Outils",
        "Profils Agent", "SESSION ACTIVE", "ARRÊTER LE CONTRÔLE", "Contrôle par Hermes", "Relais VPS (WebSocket)",
        "Journal d'activité et audits", "Inactif (requis pour le contrôle)", "Cloner la configuration de base",
        "Nouvelle tâche Kanban", "Zone Commune",
    )

    @Test
    fun `no retired label is left in a screen`() {
        val offenders = File("src/main/java/com/example/hermes/features").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    retired.firstOrNull { "\"$it" in line || "$it\"" in line }?.let { "${file.name}:${i + 1} « $it »" }
                }
            }
            .toList()
        assertTrue("Retired labels: $offenders", offenders.isEmpty())
    }
}
