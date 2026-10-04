package com.example.hermes.core.mobilecontrol

/** When the control channel must be protected by a foreground service. */
object ForegroundPolicy {
    /**
     * A pending start counts too: the process must stay alive for the relay's confirmation to arrive.
     */
    fun shouldRunForeground(view: SessionView): Boolean = view.active != null || view.pending != null
}
