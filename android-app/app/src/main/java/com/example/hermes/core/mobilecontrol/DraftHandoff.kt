package com.example.hermes.core.mobilecontrol

class DraftSessionExpired : Exception()

/** A downloaded document may leave the app only after authorization is rechecked and control has ended. */
suspend fun <T> handoffDraft(
    prepare: suspend () -> T,
    authorized: () -> Boolean,
    endControl: () -> Unit,
    open: (T) -> Unit,
    discard: (T) -> Unit,
) {
    val prepared = prepare()
    try {
        if (!authorized()) throw DraftSessionExpired()
        endControl()
        open(prepared)
    } catch (e: Exception) {
        discard(prepared)
        throw e
    }
}
