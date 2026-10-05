package com.example.hermes.core.accessibility

data class TapError(val code: String, val message: String)

/** What happened to a coordinate tap. Only an unknown outcome must never be replayed. */
enum class TapOutcome {
    /** The gesture was injected (it does not prove the app reacted). */
    TAPPED,

    /** Refused before dispatch (stale screen, hidden or disabled element, other app on top...). */
    REFUSED,

    /** The system cancelled the gesture: it was not performed. */
    CANCELLED,

    /** No answer in time: the gesture may still run, so the caller must not replay it. */
    UNKNOWN;

    fun toCommandError(elementRef: String): TapError? = when (this) {
        TAPPED -> null
        REFUSED, CANCELLED -> TapError("ACTION_FAILED", "Échec du clic sur l'élément $elementRef.")
        UNKNOWN -> TapError(
            "RESULT_UNKNOWN",
            "Résultat inconnu pour le clic par coordonnées sur $elementRef : le geste peut encore s'exécuter, ne pas le rejouer automatiquement."
        )
    }
}

object TapGuard {
    /** True only if the session's target app is still the foreground app (no dialog, shade or other app on top). */
    fun targetStillInForeground(foregroundPackage: String?, targetPackage: String): Boolean =
        !foregroundPackage.isNullOrBlank() && foregroundPackage == targetPackage

    /** A disabled element refused the click on purpose, and a hidden one is not what the user sees. */
    fun elementTappable(enabled: Boolean, visibleToUser: Boolean): Boolean = enabled && visibleToUser
}
