package com.example.idnliverecorder.recording.direct

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.idnliverecorder.MainActivity
import com.example.idnliverecorder.R
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.core.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Foreground Service for Phase 5 Direct Livestream Recording (Phase 8 Reliability).
 *
 * Ensures direct stream capture continues reliably in the background while
 * the user browses or switches applications, with a persistent recording notification,
 * synchronized finalization, and completion notifications with deep links to the Recording Library.
 */
class DirectRecordingService : Service() {

    companion object {
        private const val TAG = "DirectRecordingService"
        const val CHANNEL_ID = "direct_live_stream_recording"
        const val NOTIFICATION_ID = 2002
        const val NOTIFICATION_COMPLETED_ID = 2003
        const val NOTIFICATION_ERROR_ID = 2004

        const val ACTION_START_DIRECT = "com.example.idnliverecorder.ACTION_START_DIRECT_RECORDING"
        const val ACTION_STOP_DIRECT = "com.example.idnliverecorder.ACTION_STOP_DIRECT_RECORDING"

        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_MEMBER_NAME = "extra_member_name"
        const val EXTRA_IS_AUTHORIZED = "extra_is_authorized"

        private val _engineStatus = MutableStateFlow(DirectStreamStatus.SOURCE_UNAVAILABLE)
        val engineStatus: StateFlow<DirectStreamStatus> = _engineStatus.asStateFlow()

        private val _recordingDurationMs = MutableStateFlow(0L)
        val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

        private val _currentMemberName = MutableStateFlow<String?>(null)
        val currentMemberName: StateFlow<String?> = _currentMemberName.asStateFlow()

        fun isRecording(): Boolean = _engineStatus.value == DirectStreamStatus.RECORDING
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var recordingEngine: DirectStreamRecordingEngine? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var startTimeMs = 0L
    private val isStoppingInProgress = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DirectRecordingService created.")
        createNotificationChannel()
        registerNetworkMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        Log.d(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_START_DIRECT -> {
                val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
                val memberName = intent.getStringExtra(EXTRA_MEMBER_NAME) ?: "JKT48"
                val isAuthorized = intent.getBooleanExtra(EXTRA_IS_AUTHORIZED, true)

                startDirectRecording(streamUrl, memberName, isAuthorized)
            }

            ACTION_STOP_DIRECT -> {
                stopDirectRecording()
            }
        }

        return START_NOT_STICKY
    }

    private fun startDirectRecording(streamUrl: String, memberName: String, isAuthorized: Boolean) {
        if (isStoppingInProgress.get()) {
            Log.w(TAG, "Ignoring direct recording start while the current session is finalizing.")
            return
        }

        if (recordingEngine?.isRunning == true) {
            Log.w(TAG, "Ignoring duplicate direct recording start; an engine is already running.")
            return
        }

        isStoppingInProgress.set(false)
        _currentMemberName.value = memberName
        startTimeMs = System.currentTimeMillis()
        _recordingDurationMs.value = 0L

        // Start Foreground immediately
        val initialNotification = buildNotification(memberName, 0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        lateinit var newEngine: DirectStreamRecordingEngine
        newEngine = DirectStreamRecordingEngine(
            context = applicationContext,
            streamUrl = streamUrl,
            memberName = memberName,
            isRecordingAuthorized = isAuthorized,
            onStatusChanged = { status, msg ->
                if (recordingEngine !== newEngine) {
                    Log.w(TAG, "Ignoring status from an inactive recording engine: $status")
                } else {
                    _engineStatus.value = status
                    Log.d(TAG, "Engine status changed: $status — $msg")
                    if (status == DirectStreamStatus.COMPLETED || status == DirectStreamStatus.ERROR) {
                        stopDirectRecording()
                    }
                }
            }
        )
        recordingEngine = newEngine

        val started = newEngine.start()
        if (!started) {
            Log.e(TAG, "Direct recording engine failed to start.")
            stopDirectRecording()
            return
        }

        _engineStatus.value = DirectStreamStatus.RECORDING

        // Start 1-second duration timer for notification updates
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _engineStatus.value == DirectStreamStatus.RECORDING) {
                delay(1000)
                val duration = System.currentTimeMillis() - startTimeMs
                _recordingDurationMs.value = duration
                updateNotification(memberName, duration)
            }
        }
    }

    private fun stopDirectRecording() {
        if (!isStoppingInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Stop already in progress for DirectRecordingService.")
            return
        }

        Log.d(TAG, "Stopping DirectRecordingService gracefully...")
        timerJob?.cancel()
        timerJob = null

        _engineStatus.value = DirectStreamStatus.FINALIZING

        serviceScope.launch {
            val engine = recordingEngine
            val summary = withContext(NonCancellable) {
                engine?.stopAndAwait()
            }

            recordingEngine?.release()
            recordingEngine = null

            val savedMemberName = _currentMemberName.value ?: "JKT48"
            _currentMemberName.value = null

            if (summary != null && summary.isSuccess) {
                _engineStatus.value = DirectStreamStatus.COMPLETED
                showCompletionNotification(
                    memberName = savedMemberName,
                    displayName = summary.displayName,
                    sizeBytes = summary.sizeBytes,
                    videoUri = summary.uri
                )
            } else {
                _engineStatus.value = DirectStreamStatus.ERROR
                showErrorNotification(
                    title = "Perekaman Selesai dengan Masalah",
                    message = summary?.errorMessage ?: "Gagal menyimpan file rekaman stream."
                )
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isStoppingInProgress.set(false)
        }
    }

    private fun showCompletionNotification(
        memberName: String,
        displayName: String,
        sizeBytes: Long,
        videoUri: Uri?
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "library")
            if (videoUri != null) {
                putExtra("video_uri", videoUri.toString())
            }
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeFormatted = sizeBytes.formatFileSize()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rekaman Selesai — $memberName")
            .setContentText("File: $displayName ($sizeFormatted)")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_view, "Buka Library", openAppPendingIntent)
            .build()

        nm.notify(NOTIFICATION_COMPLETED_ID, notification)
    }

    private fun showErrorNotification(title: String, message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 3, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(NOTIFICATION_ERROR_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Direct Stream Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status perekaman direct live stream di latar belakang"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(memberName: String, durationMs: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DirectRecordingService::class.java).apply {
            action = ACTION_STOP_DIRECT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val durationFormatted = durationMs.formatDuration()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Merekam Direct Stream: $memberName")
            .setContentText("Durasi: $durationFormatted • IDN Live di Latar Belakang")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(memberName: String, durationMs: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(memberName, durationMs))
    }

    private fun registerNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    Log.w(TAG, "Network connection lost during background recording.")
                }

                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network reconnected during background recording.")
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DirectRecordingService destroyed.")
        timerJob?.cancel()
        serviceScope.cancel()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        recordingEngine?.release()
        recordingEngine = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
