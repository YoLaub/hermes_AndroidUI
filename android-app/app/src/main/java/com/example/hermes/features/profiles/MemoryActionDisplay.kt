package com.example.hermes.features.profiles

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val REQUESTED_AT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC)

private const val UNTRUSTED_CONTENT = "has read untrusted content"

/** What the action is, in words. An unknown kind is shown as sent rather than guessed. */
internal fun actionKindLabel(kind: String?): String = when (kind) {
    null -> "Type inconnu"
    "run" -> "Exécuter une commande"
    "navigate" -> "Ouvrir des adresses web"
    "tool" -> "Utiliser un outil"
    "write" -> "Écrire un fichier"
    "read" -> "Lire un fichier"
    "delete" -> "Supprimer"
    "send" -> "Envoyer un message"
    "spend" -> "Dépenser de l'argent"
    "code" -> "Exécuter un programme écrit par l'agent"
    "delegate" -> "Confier une tâche à un sous-agent"
    else -> kind
}

/** The gate's reason, explained when it is one we know and kept verbatim otherwise. */
internal fun actionReasonText(reason: String?): String = when {
    reason == null -> ""
    reason.contains(UNTRUSTED_CONTENT) ->
        "Cette session a lu du contenu non fiable (page web, message…) : elle ne peut rien écrire ni exécuter seule."
    else -> reason
}

/** The first block of the id: what `python -m agent_memory.admin actions` prints. */
internal fun shortId(id: String): String = id.substringBefore('-')

internal fun formatRequestedAt(value: String?): String {
    if (value == null) return "heure inconnue"
    return try {
        REQUESTED_AT_FORMAT.format(Instant.parse(value))
    } catch (_: Exception) {
        value
    }
}
