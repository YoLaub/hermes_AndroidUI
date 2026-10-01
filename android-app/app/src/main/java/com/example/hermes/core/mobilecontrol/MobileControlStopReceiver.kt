package com.example.hermes.core.mobilecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MobileControlStopReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MobileControlStopRecv"
        var onStopRequested: (() -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == MobileControlNotificationHelper.ACTION_STOP_SESSION) {
            Log.i(TAG, "Stop mobile control requested from notification action")
            onStopRequested?.invoke()
        }
    }
}
