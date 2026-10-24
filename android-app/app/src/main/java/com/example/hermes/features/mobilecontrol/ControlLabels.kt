package com.example.hermes.features.mobilecontrol

/** The journal's statuses in French; an unknown one is shown as sent. */
fun auditStatusLabel(status: String): String = when (status.uppercase()) {
    "STARTED" -> "Démarrée"
    "SUCCESS" -> "Réussie"
    "FAILED" -> "Échouée"
    "DENIED" -> "Refusée"
    "CONFIRMED" -> "Confirmée"
    "STOPPED" -> "Arrêtée"
    else -> status
}

/** A relay socket failure: a sentence for the user, the low-level message kept as a detail. */
data class RelayError(val message: String, val detail: String?)

fun relayError(raw: String?): RelayError = RelayError("Connexion au relais interrompue", raw?.takeIf { it.isNotBlank() })
