package com.example.hermes.features.profiles

/** A message for the user; never the raw response, which could carry text from agents or the web. */
internal fun validationErrorMessage(error: Throwable): String {
    val text = error.message.orEmpty()
    return when {
        text.contains("Jeton propriétaire non configuré") -> "Saisis d'abord le jeton propriétaire"
        text.contains("Adresse du relais non configurée") -> "Renseigne d'abord l'adresse du relais mobile"
        text.startsWith("HTTP 429") -> "Trop d'essais, réessaie dans quelques minutes"
        text.startsWith("HTTP 401") || text.startsWith("HTTP 403") -> "Jeton propriétaire refusé ou absent"
        text.startsWith("HTTP 404") -> "Élément introuvable"
        text.startsWith("HTTP 409") -> "Élément non validable"
        text.startsWith("HTTP 503") -> "Service mémoire non configuré"
        text.startsWith("HTTP 502") -> "Service mémoire injoignable"
        text.startsWith("HTTP ") -> "Erreur du service mémoire"
        else -> "Erreur réseau"
    }
}

