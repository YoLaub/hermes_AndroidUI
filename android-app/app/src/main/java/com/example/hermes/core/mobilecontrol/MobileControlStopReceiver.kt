package com.example.hermes.core.mobilecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.hermes.HermesApp

/**
 * Kill-switch of the notification. It resolves the manager through the process-scoped container
 * instead of a callback the manager registers: after a process restart a stale notification would
 * otherwise have a dead button. The notification is cancelled in every case.
 */
class MobileControlStopReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MobileControlStopRecv"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != MobileControlNotificationHelper.ACTION_STOP_SESSION) return
        Log.i(TAG, "event=stop_requested_from_notification")
        (context.applicationContext as? HermesApp)?.container?.mobileControlManager
            ?.stopSession("user_stopped_via_notification")
        MobileControlNotificationHelper(context).cancelSessionNotification()
    }
}
