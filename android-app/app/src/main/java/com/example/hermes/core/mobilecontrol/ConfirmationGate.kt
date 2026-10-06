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
    val readKinds: Set<String>,
    /** For changes that are not a message or a call (calendar): the question and the exact change shown. */
    val headline: String? = null,
    val details: String? = null
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

    /** The question at the top of the confirmation. */
    fun titleOf(r: ConfirmationRequest): String = when (r.kind) {
        "sms_send" -> "Envoyer ce SMS à ${r.recipientName} (${r.recipientNumber}) ?"
        "call_place" -> "Appeler ${r.recipientName} (${r.recipientNumber}) ?"
        else -> r.headline ?: "Hermes demande votre accord."
    }

    /** The exact content being confirmed, then the banner about what was read. Empty when there is nothing to add. */
    fun bodyOf(r: ConfirmationRequest): String {
        val core = when (r.kind) {
            "sms_send" -> r.text?.let { "« $it »" }
            "call_place" -> null
            else -> r.details
        }
        return listOfNotNull(core, banner(r.readKinds)).joinToString("\n\n")
    }

    fun banner(readKinds: Set<String>): String? {
        val parts = LABELS.filterKeys { it in readKinds }.values
        if (parts.isEmpty()) return null
        return "Proposé après lecture de : " + parts.joinToString(", ")
    }
}
