package com.example.hermes.core.mobilecontrol

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.hermes.HermesApp

/**
 * Foreground service held only while a mobile-control session is pending or active. It does not run
 * the WebSocket itself (the process-scoped [MobileControlManager] does): its job is to keep the
 * process alive and visible to the user, with the kill-switch notification.
 */
class MobileControlService : Service() {

    companion object {
        private const val TAG = "MobileControlService"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MobileControlService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses to start one from the background. The ordinary ongoing
                // notification still shows; the channel just is not protected by the service.
                Log.w(TAG, "event=fgs_start_refused error=${e.javaClass.simpleName}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MobileControlService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as HermesApp).container.mobileControlManager
        val view = SessionView(pending = manager.pendingSession.value, active = manager.activeSession.value)

        // startForegroundService() obliges this call within seconds, even if the session already ended:
        // stopping first would crash the process (ForegroundServiceDidNotStartInTimeException).
        val content = ForegroundPolicy.notificationFor(view)
        val notification = MobileControlNotificationHelper(this).buildNotification(content.title, content.text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(MobileControlNotificationHelper.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(MobileControlNotificationHelper.NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "event=fgs_started active=${view.active != null} pending=${view.pending != null}")

        if (!ForegroundPolicy.shouldRunForeground(view)) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "event=fgs_stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
