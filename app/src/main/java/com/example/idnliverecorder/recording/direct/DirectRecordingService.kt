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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.idnliverecorder.MainActivity
import com.example.idnliverecorder.R
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
import java.util.UUID

data class DirectRecordingSessionState(
    val sessionId: String,
    val memberName: String,
    val status: DirectStreamStatus,
    val startedAtMs: Long,
    val durationMs: Long
)

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
        const val ACTION_APP_ENTERED_FOREGROUND = "com.example.idnliverecorder.ACTION_APP_ENTERED_FOREGROUND"
        const val ACTION_APP_ENTERED_BACKGROUND = "com.example.idnliverecorder.ACTION_APP_ENTERED_BACKGROUND"

        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_MEMBER_NAME = "extra_member_name"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val MAX_CONCURRENT_RECORDINGS = 5

        private val _engineStatus = MutableStateFlow(DirectStreamStatus.SOURCE_UNAVAILABLE)
        val engineStatus: StateFlow<DirectStreamStatus> = _engineStatus.asStateFlow()

        private val _recordingSessions = MutableStateFlow<List<DirectRecordingSessionState>>(emptyList())
        val recordingSessions: StateFlow<List<DirectRecordingSessionState>> = _recordingSessions.asStateFlow()

        fun isRecording(): Boolean = _recordingSessions.value.any { it.status == DirectStreamStatus.RECORDING }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private data class ActiveSession(
        val id: String,
        val memberName: String,
        val startedAtMs: Long,
        val engine: DirectStreamRecordingEngine,
        var status: DirectStreamStatus = DirectStreamStatus.READY_TO_RECORD,
        var isStopping: Boolean = false
    )
    private val sessions = linkedMapOf<String, ActiveSession>()
    private var isForegroundStarted = false
    private var latestStartId = 0
    private val backgroundTimeoutPolicy = RecordingBackgroundTimeoutPolicy()
    private var backgroundStopJob: Job? = null
    private var wasStoppedBySystemTimeout = false

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DirectRecordingService created.")
        createNotificationChannel()
        registerNetworkMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val action = intent?.action ?: return START_NOT_STICKY
        Log.d(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_START_DIRECT -> {
                val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
                val memberName = intent.getStringExtra(EXTRA_MEMBER_NAME) ?: "JKT48"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: UUID.randomUUID().toString()
                startDirectRecording(sessionId, streamUrl, memberName)
            }

            ACTION_STOP_DIRECT -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (sessionId.isNullOrBlank()) stopDirectRecording() else stopDirectRecording(sessionId)
            }

            ACTION_APP_ENTERED_FOREGROUND -> handleAppEnteredForeground()
            ACTION_APP_ENTERED_BACKGROUND -> handleAppEnteredBackground()
        }

        return START_NOT_STICKY
    }

    private fun startDirectRecording(sessionId: String, streamUrl: String, memberName: String) {
        if (sessionId in sessions) return
        if (sessions.size >= MAX_CONCURRENT_RECORDINGS) {
            showErrorNotification("Batas rekaman tercapai", "Maksimal $MAX_CONCURRENT_RECORDINGS live dapat direkam bersamaan.")
            return
        }
        if (streamUrl.isBlank()) {
            showErrorNotification("Sumber live tidak tersedia", "URL stream untuk $memberName kosong.")
            return
        }

        lateinit var engine: DirectStreamRecordingEngine
        engine = DirectStreamRecordingEngine(
            context = applicationContext,
            streamUrl = streamUrl,
            memberName = memberName,
            onStatusChanged = { status, message ->
                serviceScope.launch {
                    val active = sessions[sessionId]
                    if (active == null || active.engine !== engine) return@launch
                    active.status = status
                    publishSessionState()
                    Log.d(TAG, "[$sessionId] Engine status changed: $status - $message")
                    updateNotification()
                    if (status == DirectStreamStatus.COMPLETED ||
                        status == DirectStreamStatus.ERROR ||
                        status == DirectStreamStatus.SOURCE_UNAVAILABLE
                    ) stopDirectRecording(sessionId)
                }
            }
        )
        val session = ActiveSession(sessionId, memberName, System.currentTimeMillis(), engine)
        sessions[sessionId] = session
        publishSessionState()

        if (!isForegroundStarted) {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else startForeground(NOTIFICATION_ID, notification)
            } else startForeground(NOTIFICATION_ID, notification)
            isForegroundStarted = true
        } else updateNotification()

        if (!engine.start()) {
            stopDirectRecording(sessionId)
            return
        }
        session.status = DirectStreamStatus.RECORDING
        publishSessionState()
        startTimerIfNeeded()
        updateNotification()
    }

    private fun handleAppEnteredForeground() {
        backgroundTimeoutPolicy.onAppForegrounded()
        backgroundStopJob?.cancel()
        backgroundStopJob = null
    }

    private fun handleAppEnteredBackground() {
        if (sessions.isEmpty()) return
        backgroundTimeoutPolicy.onAppBackgrounded(SystemClock.elapsedRealtime())
        backgroundStopJob?.cancel()
        val remainingMs = backgroundTimeoutPolicy.millisUntilGracefulStop(SystemClock.elapsedRealtime()) ?: return
        backgroundStopJob = serviceScope.launch {
            delay(remainingMs)
            if (sessions.isNotEmpty() &&
                backgroundTimeoutPolicy.millisUntilGracefulStop(SystemClock.elapsedRealtime()) == 0L
            ) {
                showErrorNotification(
                    title = "Rekaman dihentikan otomatis",
                    message = "Batas aman perekaman di latar belakang tercapai. File yang ada sedang diselesaikan."
                )
                stopDirectRecording()
            }
        }
    }

    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        Log.e(TAG, "Android data-sync foreground-service timeout reached; stopping active engines immediately.")
        showErrorNotification(
            title = "Batas waktu Android tercapai",
            message = "Sesi dihentikan oleh sistem; file yang sedang direkam mungkin belum selesai disimpan."
        )
        wasStoppedBySystemTimeout = true
        backgroundStopJob?.cancel()
        sessions.values.toList().forEach { it.engine.stop() }
        if (isForegroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
        stopSelf(startId)
    }

    private fun stopDirectRecording(sessionId: String? = null) {
        sessions.values.filter { sessionId == null || it.id == sessionId }.forEach { session ->
            if (session.isStopping) return@forEach
            session.isStopping = true
            session.status = DirectStreamStatus.FINALIZING
            publishSessionState()
            serviceScope.launch {
                val summary = withContext(NonCancellable) { session.engine.stopAndAwait() }
                session.engine.release()
                sessions.remove(session.id)
                if (summary?.isSuccess == true) {
                    showCompletionNotification(
                        memberName = session.memberName,
                        displayName = summary.displayName,
                        sizeBytes = summary.sizeBytes,
                        videoUri = summary.uri,
                        warningMessage = summary.warningMessage
                    )
                    _engineStatus.value = DirectStreamStatus.COMPLETED
                } else {
                    showErrorNotification(
                        title = "Perekaman ${session.memberName} bermasalah",
                        message = summary?.errorMessage ?: "Gagal menyimpan file rekaman stream."
                    )
                    _engineStatus.value = DirectStreamStatus.ERROR
                }
                publishSessionState()
                updateNotification()
                if (sessions.isEmpty()) {
                    timerJob?.cancel()
                    timerJob = null
                    if (isForegroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
                    isForegroundStarted = false
                    stopSelfResult(latestStartId)
                }
            }
        }
    }

    private fun publishSessionState() {
        val now = System.currentTimeMillis()
        val states = sessions.values.map { session ->
            DirectRecordingSessionState(
                sessionId = session.id,
                memberName = session.memberName,
                status = session.status,
                startedAtMs = session.startedAtMs,
                durationMs = (now - session.startedAtMs).coerceAtLeast(0L)
            )
        }
        _recordingSessions.value = states
        if (states.any { it.status == DirectStreamStatus.RECORDING }) {
            _engineStatus.value = DirectStreamStatus.RECORDING
        } else if (states.any { it.status == DirectStreamStatus.FINALIZING }) {
            _engineStatus.value = DirectStreamStatus.FINALIZING
        } else if (states.isEmpty() &&
            _engineStatus.value != DirectStreamStatus.COMPLETED &&
            _engineStatus.value != DirectStreamStatus.ERROR
        ) {
            _engineStatus.value = DirectStreamStatus.SOURCE_UNAVAILABLE
        }
    }

    private fun startTimerIfNeeded() {
        if (timerJob?.isActive == true) return
        timerJob = serviceScope.launch {
            while (isActive && sessions.isNotEmpty()) {
                delay(1000)
                publishSessionState()
                updateNotification()
            }
        }
    }
    private fun showCompletionNotification(
        memberName: String,
        displayName: String,
        sizeBytes: Long,
        videoUri: Uri?,
        warningMessage: String?
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
            .setContentTitle(if (warningMessage == null) "Rekaman Selesai - $memberName" else "Rekaman Parsial - $memberName")
            .setContentText(warningMessage ?: "File: $displayName ($sizeFormatted)")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_view, "Buka Library", openAppPendingIntent)
            .build()

        val notificationId = 50_000 + (displayName.hashCode() and 0x7FFF)
        nm.notify(notificationId, notification)
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

        val notificationId = 100_000 + (title.hashCode() and 0x7FFF)
        nm.notify(notificationId, notification)
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

    private fun buildNotification(): Notification {
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

        val activeCount = sessions.size
        val memberSummary = sessions.values.take(3).joinToString(", ") { it.memberName }
        val extraCount = (sessions.size - 3).coerceAtLeast(0)
        val summary = if (extraCount > 0) "$memberSummary, +$extraCount lainnya" else memberSummary

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Merekam $activeCount live")
            .setContentText(summary.ifBlank { "Perekaman live berjalan di latar belakang" })
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Hentikan semua", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        if (!isForegroundStarted || sessions.isEmpty()) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
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
        if (!wasStoppedBySystemTimeout) {
            sessions.values.forEach { it.engine.release() }
        }
        sessions.clear()
        _recordingSessions.value = emptyList()
        if (_engineStatus.value == DirectStreamStatus.RECORDING ||
            _engineStatus.value == DirectStreamStatus.FINALIZING
        ) _engineStatus.value = DirectStreamStatus.SOURCE_UNAVAILABLE
        isForegroundStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
