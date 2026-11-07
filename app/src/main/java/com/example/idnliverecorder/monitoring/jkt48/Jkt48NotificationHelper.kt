package com.example.idnliverecorder.monitoring.jkt48

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.idnliverecorder.MainActivity
import com.example.idnliverecorder.R

/**
 * Handles Android notifications for favorite JKT48 member live streams.
 */
class Jkt48NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "jkt48_live_notification_channel"
        private const val BASE_NOTIFICATION_ID = 3000
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JKT48 Live Notifikasi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat member favorit JKT48 sedang live di IDN"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a notification when a favorite member starts an IDN Live stream.
     */
    fun notifyFavoriteLive(member: Jkt48LiveMember) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_tab", 1) // Opens JKT48 Live Monitor tab
            putExtra("streamer_name", member.memberName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            member.roomId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = BASE_NOTIFICATION_ID + (member.roomId.hashCode() % 1000).let { if (it < 0) -it else it }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⭐ ${member.memberName} Sedang LIVE!")
            .setContentText(member.title?.ifBlank { null } ?: "Member favoritmu sedang live streaming di IDN Live!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "⭐ ${member.memberName} sedang live di IDN!\n${member.title ?: ""}\nKetuk untuk melihat detail dan siap merekam."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, notification)
    }
}

