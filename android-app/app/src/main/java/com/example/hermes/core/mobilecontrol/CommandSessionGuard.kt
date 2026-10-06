package com.example.hermes.core.mobilecontrol

data class CommandRejection(val code: String, val message: String)

/**
 * Session and mode checks for an incoming relay command, as a pure function.
 *
 * Each "no session" situation has its own code so the agent (and the logs) can tell
 * where the disagreement is: the relay says SESSION_REQUIRED when IT has no session;
 * the phone answers SESSION_NOT_ON_PHONE / SESSION_ID_MISMATCH when the relay has one
 * and the phone does not, or holds a different one.
 */
object CommandSessionGuard {

    // A consented screenshot is allowed in observation mode: it taps and types nothing.
    private val OBSERVATION_ALLOWED = setOf("observe", "end_session", "screenshot", "calendar_read")

    fun validate(
        active: MobileControlSession?,
        pendingSessionId: String?,
        cmd: MobileCommand,
        nowMs: Long
    ): CommandRejection? {
        if (active == null) {
            if (pendingSessionId != null && pendingSessionId == cmd.sessionId) {
                return CommandRejection(
                    "SESSION_PENDING_ON_PHONE",
                    "Le téléphone attend encore la confirmation du relais pour cette session. Réessayez dans un instant."
                )
            }
            return CommandRejection(
                "SESSION_NOT_ON_PHONE",
                "Le téléphone n'a aucune session de contrôle active alors que le relais en a une (${cmd.sessionId})."
            )
        }
        if (active.id != cmd.sessionId) {
            return CommandRejection(
                "SESSION_ID_MISMATCH",
                "Le téléphone a une autre session (${active.id}) que celle du relais (${cmd.sessionId})."
            )
        }
        if (nowMs >= active.expiresAt) {
            return CommandRejection("SESSION_EXPIRED", "La session a expiré sur le téléphone.")
        }
        if (cmd.targetPackage != active.targetPackage) {
            return CommandRejection(
                "APP_NOT_ALLOWED",
                "Le package demandé (${cmd.targetPackage}) ne correspond pas à la session (${active.targetPackage})."
            )
        }
        if (active.mode == MobileControlMode.OBSERVATION && cmd.operation !in OBSERVATION_ALLOWED) {
            return CommandRejection(
                "MODE_DENIED",
                "La session est en mode observation seule. Clics et saisies refusés."
            )
        }
        val needed = Consent.requiredFor(cmd.operation)
        if (needed != null && !active.allows(needed)) {
            return CommandRejection(Consent.refusalCode(needed), Consent.refusalMessage(needed))
        }
        return null
    }
}
