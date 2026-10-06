package com.example.hermes.core.mobilecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.hermes.HermesApp

/**
 * The buttons of the confirmation notification. Not exported and without intent filter: only the explicit
 * PendingIntents created by [ConfirmationNotifier] can reach it. The decision goes to the manager, which ignores
 * anything that is not the pending request.
 */
class ConfirmationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ConfirmationNotifier.ACTION_DECIDE) return
        val id = intent.getStringExtra(ConfirmationNotifier.EXTRA_ID) ?: return
        val accept = intent.getBooleanExtra(ConfirmationNotifier.EXTRA_ACCEPT, false)
        Log.i("ConfirmationReceiver", "event=confirmation_decision accept=$accept")
        (context.applicationContext as? HermesApp)?.container?.mobileControlManager?.onConfirmationDecision(id, accept)
        ConfirmationNotifier(context).dismiss()
    }
}
