package com.example.idnliverecorder.monitoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.idnliverecorder.MainActivity
import com.example.idnliverecorder.R
import com.example.idnliverecorder.core.Constants
import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.data.DetectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LiveDetectionManager(private val context: Context) {

    companion object {
        private const val TAG = "LiveDetectionManager"

        @Volatile
        private var INSTANCE: LiveDetectionManager? = null

        fun getInstance(context: Context): LiveDetectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveDetectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val settings = LiveDetectionSettings.getInstance(context)
    private val repository = LiveDetectionRepository.getInstance(context)

    private val _isPermissionGranted = MutableStateFlow(checkNotificationAccessGranted())
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    init {
        createDetectionNotificationChannel()
    }

    fun refreshPermissionStatus(): Boolean {
        val granted = checkNotificationAccessGranted()
        _isPermissionGranted.value = granted
        return granted
    }

    fun checkNotificationAccessGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun isDetectionActive(): Boolean {
        val currentSettings = settings.settingsFlow.value
        val hasPackage = !currentSettings.targetPackageName.isNullOrBlank()
        val hasPermission = checkNotificationAccessGranted()
        return currentSettings.isEnabled && hasPackage && hasPermission
    }

    fun processNotification(
        notificationKey: String,
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?
    ): Boolean {
        val currentSettings = settings.settingsFlow.value

        // Check if detection enabled
        if (!currentSettings.isEnabled) {
            Log.d(TAG, "Detection disabled in settings. Ignoring notification.")
            return false
        }

        // Parse notification
        val parseResult = LiveNotificationParser.parse(
            packageName = packageName,
            targetPackageName = currentSettings.targetPackageName,
            title = title,
            text = text,
            bigText = bigText,
            keywords = currentSettings.keywords
        )

        when (parseResult) {
            is ParseResult.Match -> {
                Log.d(TAG, "Live notification matched: Streamer='${parseResult.streamerName}', Keyword='${parseResult.matchedKeyword}'")

                // Check duplicate & cooldown
                val isDebounced = repository.isDuplicateOrInCooldown(
                    notificationKey = notificationKey,
                    streamerName = parseResult.streamerName,
                    cooldownMinutes = currentSettings.cooldownMinutes
                )

                if (isDebounced) {
                    Log.d(TAG, "Notification for '${parseResult.streamerName}' suppressed by cooldown (${currentSettings.cooldownMinutes}m).")
                    return false
                }

                // Add to repository
                val detectionItem = repository.addDetection(
                    notificationKey = notificationKey,
                    streamerName = parseResult.streamerName,
                    content = parseResult.fullContent,
                    packageName = packageName,
                    matchedKeyword = parseResult.matchedKeyword
                )

                // Show notification if enabled
                if (currentSettings.notificationsEnabled) {
                    showDetectionNotification(detectionItem)
                }

                return true
            }

            ParseResult.PackageMismatch -> {
                // Not from selected package
                return false
            }

            ParseResult.NoKeywordMatch -> {
                Log.d(TAG, "Notification from target app but no live keywords found.")
                return false
            }

            ParseResult.EmptyContent -> {
                Log.d(TAG, "Notification from target app has empty title and text.")
                return false
            }
        }
    }

    fun simulateTestDetection(streamerName: String, sampleMessage: String = "sedang live sekarang!") {
        val currentSettings = settings.settingsFlow.value
        val fakeKey = "test_key_${UUID.randomUUID()}"
        val targetPkg = currentSettings.targetPackageName ?: "com.idn.app.test"

        val item = repository.addDetection(
            notificationKey = fakeKey,
            streamerName = streamerName,
            content = "$streamerName $sampleMessage",
            packageName = targetPkg,
            matchedKeyword = "live"
        )

        showDetectionNotification(item)
    }

    private fun showDetectionNotification(item: DetectedLiveItem) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent for "Lihat" -> Opens MainActivity with detection confirmation
        val viewIntent = Intent(context, MainActivity::class.java).apply {
            action = Constants.ACTION_VIEW_DETECTION
            putExtra(Constants.EXTRA_DETECTION_ID, item.id)
            putExtra(Constants.EXTRA_STREAMER_NAME, item.streamerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Abaikan" -> BroadcastReceiver to dismiss & mark ignored
        val ignoreIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = Constants.ACTION_IGNORE_DETECTION
            putExtra(Constants.EXTRA_DETECTION_ID, item.id)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.hashCode() + 1,
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.detection_title)
        val body = context.getString(R.string.detection_body, item.streamerName)

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID_DETECTION)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.streamerName} terdeteksi sedang live streaming!\n${item.notificationContent}"))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(viewPendingIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.action_view), viewPendingIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.action_ignore), ignorePendingIntent)
            .build()

        notificationManager.notify(Constants.NOTIFICATION_ID_LIVE_DETECTION, notification)
    }

    fun dismissDetectionNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Constants.NOTIFICATION_ID_LIVE_DETECTION)
    }

    private fun createDetectionNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID_DETECTION,
                context.getString(R.string.notification_channel_detection_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_detection_desc)
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

