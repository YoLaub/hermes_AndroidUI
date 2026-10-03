package com.example.hermes.core.mobilecontrol

/**
 * What the phone believes about its session.
 * [pending]: start requested, relay has not answered. Never shown as active.
 * [active]: confirmed by the relay.
 */
data class SessionView(val pending: MobileControlSession?, val active: MobileControlSession?)

data class SessionTransition(
    val view: SessionView,
    /** Message for the user, null when nothing noteworthy happened. */
    val notice: String? = null,
    /** Set when a locally active session was ended because the relay no longer has it. */
    val endedLocally: MobileControlSession? = null
)

/**
 * Pure rules deciding when a session becomes active and how the phone reconciles with
 * the relay. The relay is the source of truth: a session is active only after
 * session_started_ack (or a session_state that confirms it).
 */
object SessionConfirmation {

    fun onAck(view: SessionView, ack: MobileSessionStartedAck, nowMs: Long): SessionTransition {
        val pending = view.pending
        if (pending == null || pending.id != ack.sessionId) return SessionTransition(view)
        val confirmed = withServerExpiry(pending, ack.expiresInSeconds, nowMs)
        return SessionTransition(SessionView(pending = null, active = confirmed))
    }

    fun onError(view: SessionView, err: MobileSessionError): SessionTransition {
        val pending = view.pending
        val matches = pending != null && (err.sessionId.isNullOrBlank() || err.sessionId == pending.id)
        if (!matches) return SessionTransition(view, notice = describeError(err))
        return SessionTransition(view.copy(pending = null), notice = describeError(err))
    }

    fun onTimeout(view: SessionView, sessionId: String): SessionTransition {
        val pending = view.pending
        if (pending == null || pending.id != sessionId) return SessionTransition(view)
        return SessionTransition(
            view.copy(pending = null),
            notice = "Le relais n'a pas confirmé le démarrage de la session. Aucune session n'est active."
        )
    }

    fun onServerState(view: SessionView, state: MobileSessionState, nowMs: Long): SessionTransition {
        val active = view.active
        val pending = view.pending

        if (!state.active) {
            if (active == null) return SessionTransition(view)
            return SessionTransition(
                view.copy(active = null),
                notice = "Session terminée : le relais n'a plus de session pour cet appareil.",
                endedLocally = active
            )
        }

        val serverId = state.sessionId ?: return SessionTransition(view)

        if (pending != null && pending.id == serverId) {
            val confirmed = withServerExpiry(pending, state.expiresInSeconds, nowMs)
            return SessionTransition(SessionView(pending = null, active = confirmed))
        }
        if (active != null && active.id == serverId) {
            return SessionTransition(view.copy(active = withServerExpiry(active, state.expiresInSeconds, nowMs)))
        }

        // The relay holds a session this phone does not know (or a different one). The relay
        // would let the agent act on it, so show it: the user must be able to see and stop it.
        val adopted = MobileControlSession(
            id = serverId,
            targetPackage = state.targetPackage.orEmpty(),
            targetAppName = state.targetPackage.orEmpty(),
            allowedProfile = state.profile ?: "john",
            mode = if (state.mode == "observation") MobileControlMode.OBSERVATION else MobileControlMode.INTERACTION,
            startedAt = nowMs,
            durationSeconds = state.expiresInSeconds ?: 0,
            expiresAt = nowMs + (state.expiresInSeconds ?: 0) * 1000L
        )
        return SessionTransition(
            SessionView(pending = pending, active = adopted),
            notice = "Le relais a une session active que cette application ne connaissait pas : elle est maintenant affichée."
        )
    }

    private fun withServerExpiry(session: MobileControlSession, expiresInSeconds: Int?, nowMs: Long): MobileControlSession {
        if (expiresInSeconds == null) return session
        return session.copy(expiresAt = nowMs + expiresInSeconds * 1000L)
    }

    private fun describeError(err: MobileSessionError): String = when (err.errorCode) {
        "DEVICE_NOT_AUTHENTICATED" ->
            "Le relais ne reconnaît pas cet appareil : il doit être appairé avec le relais. Aucune session n'est active."
        "PROFILE_NOT_ALLOWED" ->
            "Le relais refuse ce profil : seul le profil 'john' est autorisé. Aucune session n'est active."
        else -> "Le relais a refusé la session (${err.errorCode}). Aucune session n'est active."
    }
}
