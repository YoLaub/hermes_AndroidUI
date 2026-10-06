package com.example.hermes.core.mobilecontrol

/** What the user will see and what will be executed: both are THIS object, which cannot change once shown. */
data class ConfirmationRequest(
    val id: String,
    /** "sms_send" or "call_place". */
    val kind: String,
    val recipientName: String,
    val recipientNumber: String,
    val text: String?,
    /** Kinds of external content read since the session started, shown as a banner. */
    val readKinds: Set<String>
)

enum class Decision { ACCEPTED, REFUSED, EXPIRED }

/**
 * Pure state machine for the confirmation of an irreversible action: one at a time, a delay of 60 seconds after
 * which the answer is "no", and every request resolves exactly once. Only [decide] with accept = true inside the
 * delay yields [Decision.ACCEPTED]: nothing else (a late tap, a replayed tap, another id) can.
 */
class ConfirmationGate(private val timeoutMs: Long = 60_000L) {
    enum class Open { OPENED, BUSY }

    private var current: ConfirmationRequest? = null
    private var deadlineMs: Long = 0L

    val pending: ConfirmationRequest? get() = current

    fun open(request: ConfirmationRequest, nowMs: Long): Open {
        if (current != null && nowMs < deadlineMs) return Open.BUSY
        current = request
        deadlineMs = nowMs + timeoutMs
        return Open.OPENED
    }

    /** The user's answer. Null when it does not concern the pending request (nothing changes then). */
    fun decide(id: String, accept: Boolean, nowMs: Long): Decision? {
        val req = current ?: return null
        if (req.id != id) return null
        current = null
        return when {
            nowMs >= deadlineMs -> Decision.EXPIRED
            accept -> Decision.ACCEPTED
            else -> Decision.REFUSED
        }
    }

    /** Resolves the pending request as expired once its delay is over. Returns its id, or null. */
    fun expire(nowMs: Long): String? {
        val req = current ?: return null
        if (nowMs < deadlineMs) return null
        current = null
        return req.id
    }

    /** Drops the pending request without accepting it (session ended, command cancelled). */
    fun cancel() {
        current = null
    }
}

object ConfirmationText {
    // Stable display order; "screen" stands for a plain observation of the target app.
    private val LABELS = linkedMapOf(
        "screen" to "écran",
        Consent.SCREENSHOTS to "captures",
        Consent.CALENDAR to "calendrier",
        Consent.SMS_READ to "SMS",
        Consent.CALL_LOG_READ to "journal d'appels"
    )

    fun banner(readKinds: Set<String>): String? {
        val parts = LABELS.filterKeys { it in readKinds }.values
        if (parts.isEmpty()) return null
        return "Proposé après lecture de : " + parts.joinToString(", ")
    }
}
