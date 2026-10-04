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
        if (!ForegroundPolicy.shouldRunForeground(view)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val helper = MobileControlNotificationHelper(this)
        val active = view.active
        val notification = if (active != null) {
            helper.buildNotification(
                title = "Contrôle Hermes actif : ${active.targetAppName}",
                text = "Profil autorisé : ${active.allowedProfile} • Mode : ${active.mode.name.lowercase()}"
            )
        } else {
            helper.buildNotification(
                title = "Contrôle Hermes : démarrage",
                text = "En attente de la confirmation du relais"
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(MobileControlNotificationHelper.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(MobileControlNotificationHelper.NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "event=fgs_started active=${active != null}")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "event=fgs_stopped")
        super.onDestroy()
    }
}
