package com.example.hermes.features.settings

import com.example.hermes.core.model.OpenBaoHealth

enum class RelayUrlError { Required, Scheme, Host }

/** Why a relay address cannot be saved, or null. The app talks to it over HTTP(S) then WebSocket. */
fun relayUrlError(url: String): RelayUrlError? {
    val u = url.trim()
    if (u.isEmpty()) return RelayUrlError.Required
    val rest = when {
        u.startsWith("https://", ignoreCase = true) -> u.substring(8)
        u.startsWith("http://", ignoreCase = true) -> u.substring(7)
        else -> return RelayUrlError.Scheme
    }
    return if (rest.substringBefore('/').isBlank()) RelayUrlError.Host else null
}

fun pairingCodeOrNull(code: String): String? = code.trim().ifEmpty { null }

/** One line on the vault for Réglages; [health] is null until a check succeeded. */
fun openBaoStatus(url: String, health: OpenBaoHealth?): String = when {
    url.isBlank() -> "Non configuré"
    health == null -> "Injoignable ou en attente de test"
    !health.initialized -> "Non initialisé"
    health.sealed -> "Scellé · secrets inaccessibles"
    health.version.isNotBlank() -> "Connecté · v${health.version}"
    else -> "Connecté"
}
