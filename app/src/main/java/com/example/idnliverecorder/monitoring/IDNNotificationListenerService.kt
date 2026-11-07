package com.example.idnliverecorder.monitoring

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IDNNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "IDNNotificationListener"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected.")
        _isServiceConnected.value = true
        LiveDetectionManager.getInstance(applicationContext).refreshPermissionStatus()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListenerService disconnected.")
        _isServiceConnected.value = false
        LiveDetectionManager.getInstance(applicationContext).refreshPermissionStatus()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: return
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

            val key = sbn.key ?: "${packageName}_${sbn.id}_${sbn.postTime}"

            val manager = LiveDetectionManager.getInstance(applicationContext)
            manager.processNotification(
                notificationKey = key,
                packageName = packageName,
                title = title,
                text = text,
                bigText = bigText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling posted notification: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        // No action required on removal, maintaining zero clutter
    }
}

