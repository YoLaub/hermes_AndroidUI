package com.example.hermes.core.mobilecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.hermes.MainActivity

class MobileControlNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "hermes_mobile_control_channel"
        const val NOTIFICATION_ID = 4001
        const val ACTION_STOP_SESSION = "com.example.hermes.ACTION_STOP_MOBILE_CONTROL"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Contrôle Mobile Hermes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indique qu'une session de contrôle mobile par un profil Hermes est en cours."
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showActiveSessionNotification(session: MobileControlSession) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(
            title = "Contrôle Hermes actif : ${session.targetAppName}",
            text = "Profil autorisé : ${session.allowedProfile} • Mode : ${session.mode.name.lowercase()}"
        ))
    }

    /** Notification used both for the ongoing session and as the foreground service's notification. */
    fun buildNotification(title: String, text: String): android.app.Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(context, MobileControlStopReceiver::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Arrêter le contrôle",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun cancelSessionNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
