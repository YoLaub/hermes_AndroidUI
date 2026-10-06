package com.example.hermes.core.mobilecontrol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks the user to confirm an irreversible action and waits for the answer. The action runs only if [confirm]
 * returns [Decision.ACCEPTED]; every other outcome (refusal, delay, another request in progress, session ended)
 * means "do not do it". [show] puts the request in front of the user, [dismiss] takes it away again.
 */
class ActionConfirmer(
    private val show: (ConfirmationRequest) -> Unit,
    private val dismiss: (String) -> Unit,
    private val now: () -> Long,
    private val timeoutMs: Long = 60_000L
) {
    private val gate = ConfirmationGate(timeoutMs)
    private val waiting = HashMap<String, CompletableDeferred<Decision>>()
    private val lock = Any()

    /** Null when another confirmation is already pending: nothing is shown then. */
    suspend fun confirm(request: ConfirmationRequest): Decision? {
        val answer = CompletableDeferred<Decision>()
        synchronized(lock) {
            if (gate.open(request, now()) == ConfirmationGate.Open.BUSY) return null
            waiting[request.id] = answer
        }
        show(request)
        val decision = withTimeoutOrNull(timeoutMs) { answer.await() } ?: run {
            // Our own timer ran out: whatever the gate thinks, the answer is "no".
            synchronized(lock) {
                gate.cancel()
                waiting.remove(request.id)
            }
            Decision.EXPIRED
        }
        dismiss(request.id)
        return decision
    }

    /** The user's tap, from the notification. Ignored unless it concerns the pending request. */
    fun onUserDecision(id: String, accept: Boolean) {
        val toComplete: Pair<CompletableDeferred<Decision>, Decision>? = synchronized(lock) {
            val decision = gate.decide(id, accept, now()) ?: return@synchronized null
            val deferred = waiting.remove(id) ?: return@synchronized null
            deferred to decision
        }
        toComplete?.let { (deferred, decision) -> deferred.complete(decision) }
    }

    /** The session ended or the app is going away: whatever is pending is refused. */
    fun cancelAll() {
        val pending = synchronized(lock) {
            gate.cancel()
            waiting.toMap().also { waiting.clear() }
        }
        pending.values.forEach { it.complete(Decision.REFUSED) }
    }
}
