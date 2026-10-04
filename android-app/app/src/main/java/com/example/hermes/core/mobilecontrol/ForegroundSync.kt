package com.example.hermes.core.mobilecontrol

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Drives the foreground service from the session view.
 *
 * - Every view change while a session exists calls [start] again: a start refused earlier by Android
 *   (background restriction) is retried, and the notification content is refreshed.
 * - [stop] is delayed by [stopDelayMs] and cancelled by any newer view, so the service is never
 *   stopped before it had the chance to call startForeground() (that crashes the process).
 * - Nothing is stopped that was never started.
 */
class ForegroundSync(
    private val views: Flow<SessionView>,
    private val start: () -> Unit,
    private val stop: () -> Unit,
    private val stopDelayMs: Long = 1_500
) {
    private var started = false

    suspend fun run() {
        views.collectLatest { view ->
            if (ForegroundPolicy.shouldRunForeground(view)) {
                started = true
                start()
            } else if (started) {
                delay(stopDelayMs)
                stop()
                started = false
            }
        }
    }
}
