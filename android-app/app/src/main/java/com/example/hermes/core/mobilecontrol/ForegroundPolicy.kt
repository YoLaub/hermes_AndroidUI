package com.example.hermes.core.mobilecontrol

data class ForegroundNotificationContent(val title: String, val text: String)

/** When the control channel must be protected by a foreground service, and what it shows. */
object ForegroundPolicy {
    /**
     * A pending start counts too: the process must stay alive for the relay's confirmation to arrive.
     */
    fun shouldRunForeground(view: SessionView): Boolean = view.active != null || view.pending != null

    /**
     * Always returns something to show: startForegroundService() obliges the service to call
     * startForeground() even when the session ended before the service started.
     */
    fun notificationFor(view: SessionView): ForegroundNotificationContent {
        val active = view.active
        return when {
            active != null -> ForegroundNotificationContent(
                title = "Contrôle Hermes actif : ${active.targetAppName}",
                text = "Profil autorisé : ${active.allowedProfile} • Mode : ${active.mode.name.lowercase()}" +
                    if (active.allowScreenshots) " • Captures autorisées" else ""
            )
            view.pending != null -> ForegroundNotificationContent(
                title = "Contrôle Hermes : démarrage",
                text = "En attente de la confirmation du relais"
            )
            else -> ForegroundNotificationContent(
                title = "Contrôle Hermes",
                text = "Session terminée"
            )
        }
    }
}
