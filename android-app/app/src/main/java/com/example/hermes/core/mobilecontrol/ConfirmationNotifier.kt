package com.example.hermes.core.mobilecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * The confirmation the user taps: exact recipient, exact text, a banner about what was read, and two buttons that
 * reach a non-exported receiver through explicit, immutable, one-shot PendingIntents. It disappears by itself after
 * the delay.
 */
class ConfirmationNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "hermes_mobile_confirmations"
        const val NOTIFICATION_ID = 4002
        const val ACTION_DECIDE = "com.example.hermes.ACTION_DECIDE_CONFIRMATION"
        const val EXTRA_ID = "confirmation_id"
        const val EXTRA_ACCEPT = "accept"
        private const val TIMEOUT_MS = 60_000L
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Confirmations Hermes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Demande votre accord avant qu'un SMS soit envoyé ou qu'un appel soit lancé."
                }
            )
        }
    }

    fun show(request: ConfirmationRequest) {
        val who = "${request.recipientName} (${request.recipientNumber})"
        val title = if (request.kind == "sms_send") "Envoyer ce SMS à $who ?" else "Appeler $who ?"
        val body = buildString {
            if (request.text != null) append("« ${request.text} »")
            ConfirmationText.banner(request.readKinds)?.let {
                if (isNotEmpty()) append("\n\n")
                append(it)
            }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle(title)
            .setContentText(body.ifEmpty { "Hermes demande votre accord." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifEmpty { "Hermes demande votre accord." }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Refuser", decision(request.id, accept = false))
            .addAction(android.R.drawable.ic_menu_send, if (request.kind == "sms_send") "Envoyer" else "Appeler",
                decision(request.id, accept = true))
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun dismiss() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun decision(id: String, accept: Boolean): PendingIntent {
        val intent = Intent(ACTION_DECIDE).apply {
            component = ComponentName(context, ConfirmationReceiver::class.java)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_ACCEPT, accept)
        }
        return PendingIntent.getBroadcast(
            context,
            (id.hashCode() * 2) + if (accept) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
    }
}
