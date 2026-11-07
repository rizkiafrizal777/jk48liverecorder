package com.example.idnliverecorder.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.idnliverecorder.MainActivity
import com.example.idnliverecorder.R
import com.example.idnliverecorder.core.Constants
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.recording.source.LiveSourceType
import com.example.idnliverecorder.recording.source.ScreenCaptureProvider
import com.example.idnliverecorder.storage.MediaStoreRecorderStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service managing the full MediaCodec + AudioPlaybackCapture + MediaMuxer
 * recording pipeline (Phase 3).
 *
 * Key design principles:
 * - Single-use MediaProjection token (never reused across sessions)
 * - No microphone audio — uses AudioPlaybackCaptureConfiguration only
 * - Explicit MediaProjection.Callback.onStop() handling
 * - All resources safely cleaned up on stop or error
 */
class ScreenRecordingService : Service() {

    companion object {
        private const val TAG = "ScreenRecordingService"

        private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

        fun isRecording(): Boolean = _recordingState.value is RecordingState.Recording

        fun updateState(state: RecordingState) {
            _recordingState.value = state
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var stopJob: Job? = null

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var recordingSession: RecordingSession? = null

    private var currentOutputSession: MediaStoreRecorderStorage.OutputSession? = null
    private var currentConfig: RecordingConfig? = null
    private var recordingStartTimeMs: Long = 0L
    private var currentAudioStatus: AudioCaptureStatus = AudioCaptureStatus.NotStarted

    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection.Callback.onStop() — system revoked projection.")
            mainHandler.post {
                if (_recordingState.value is RecordingState.Recording) {
                    stopRecordingInternal(systemInitiated = true, errorMessage = null)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenRecordingService created.")
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        Log.d(TAG, "onStartCommand: action=$action")

        when (action) {
            Constants.ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(Constants.EXTRA_RESULT_CODE, 0)
                val streamerName = intent.getStringExtra(Constants.EXTRA_STREAMER_NAME)
                @Suppress("DEPRECATION")
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Constants.EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Constants.EXTRA_RESULT_DATA)
                }

                if (resultCode == 0 || resultData == null) {
                    Log.e(TAG, "Missing MediaProjection token data.")
                    _recordingState.value = RecordingState.Error("Media projection permission token missing.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startRecordingInternal(resultCode, resultData, streamerName)
            }

            Constants.ACTION_STOP_RECORDING -> {
                stopRecordingInternal(systemInitiated = false, errorMessage = null)
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecordingInternal(resultCode: Int, resultData: Intent, streamerName: String? = null) {
        if (_recordingState.value is RecordingState.Recording) {
            Log.w(TAG, "Recording already active — ignoring duplicate start.")
            return
        }

        _recordingState.value = RecordingState.Starting

        // Start foreground service FIRST (required by Android 14+)
        val initialNotification = buildNotification("Initializing recorder...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, initialNotification)
        }

        try {
            // Validate encoder capabilities
            val optimalResolution = EncoderValidator.resolveOptimalConfig(applicationContext)
            if (!optimalResolution.isSupported) {
                val errorMsg = optimalResolution.reason ?: "Device encoder does not support required video format."
                throw IllegalStateException(errorMsg)
            }

            val config = optimalResolution.config
            currentConfig = config
            Log.d(TAG, "Recording config: ${config.resolutionLabel}, fallback=${optimalResolution.fallbackApplied}")

            // Create MediaStore pending output
            val outputSession = MediaStoreRecorderStorage.createPendingVideo(applicationContext)
            currentOutputSession = outputSession

            // Acquire MediaProjection — single-use token
            val projection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("Failed to obtain MediaProjection from token.")
            mediaProjection = projection
            projection.registerCallback(projectionCallback, mainHandler)

            // Check RECORD_AUDIO permission
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            // Create and start the full recording session with ScreenCaptureProvider
            val detectedMetadata = streamerName?.let {
                com.example.idnliverecorder.data.DetectedLiveItem(
                    id = java.util.UUID.randomUUID().toString(),
                    streamerName = it,
                    notificationContent = "IDN Live Notification",
                    detectedAtMs = System.currentTimeMillis(),
                    packageName = "com.idn.app",
                    matchedKeyword = "live"
                )
            }

            val session = RecordingSession(
                context = applicationContext,
                config = config,
                mediaProjection = projection,
                outputSession = outputSession,
                hasRecordAudioPermission = hasAudioPermission,
                sourceProvider = ScreenCaptureProvider(),
                streamerInfo = detectedMetadata,
                onAudioStatusChanged = { status ->
                    currentAudioStatus = status
                    val currentState = _recordingState.value
                    if (currentState is RecordingState.Recording) {
                        _recordingState.value = currentState.copy(audioStatus = status)
                    }
                }
            )
            recordingSession = session

            // Start session
            session.start(mainHandler)

            recordingStartTimeMs = System.currentTimeMillis()
            currentAudioStatus = session.getAudioStatus()

            val activeState = RecordingState.Recording(
                startTimeMs = recordingStartTimeMs,
                durationMs = 0L,
                config = config,
                outputUri = outputSession.uri,
                audioStatus = currentAudioStatus,
                sourceType = session.sourceProvider.type,
                sessionModel = session.sessionModel
            )
            _recordingState.value = activeState

            startTimer(outputSession, config, session.sourceProvider.type, session.sessionModel)
            Log.d(TAG, "Screen recording started successfully. audioStatus=$currentAudioStatus")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            cleanupResources()
            currentOutputSession?.let { sess ->
                MediaStoreRecorderStorage.discardVideo(applicationContext, sess.uri)
                currentOutputSession = null
            }
            _recordingState.value = RecordingState.Error(
                message = e.message ?: "Failed to initialize screen recorder.",
                cause = e
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTimer(
        outputSession: MediaStoreRecorderStorage.OutputSession,
        config: RecordingConfig,
        sourceType: LiveSourceType,
        sessionModel: RecordingSessionModel
    ) {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val elapsed = System.currentTimeMillis() - recordingStartTimeMs
                _recordingState.value = RecordingState.Recording(
                    startTimeMs = recordingStartTimeMs,
                    durationMs = elapsed,
                    config = config,
                    outputUri = outputSession.uri,
                    audioStatus = currentAudioStatus,
                    sourceType = sourceType,
                    sessionModel = sessionModel.copy(durationMs = elapsed)
                )
                updateNotification(elapsed)
            }
        }
    }

    private fun stopRecordingInternal(systemInitiated: Boolean, errorMessage: String?) {
        if (stopJob?.isActive == true) {
            Log.w(TAG, "Stop already in progress, ignoring duplicate stop.")
            return
        }

        Log.d(TAG, "Stopping recording. systemInitiated=$systemInitiated, error=$errorMessage")
        timerJob?.cancel()
        timerJob = null
        _recordingState.value = RecordingState.Stopping

        val session = recordingSession
        val outputSession = currentOutputSession

        stopJob = serviceScope.launch {
            var stopSuccess = false

            // Stop recording session (suspending — waits for muxer flush)
            if (session != null) {
                try {
                    session.stop()
                    stopSuccess = true
                } catch (e: Exception) {
                    Log.e(TAG, "RecordingSession.stop() error: ${e.message}", e)
                }
            }

            // Stop MediaProjection
            try {
                mediaProjection?.unregisterCallback(projectionCallback)
                mediaProjection?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "MediaProjection stop error: ${e.message}")
            }
            mediaProjection = null
            recordingSession = null

            // Close output file descriptor
            try {
                outputSession?.parcelFileDescriptor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "PFD close error: ${e.message}")
            }

            if (outputSession != null) {
                if (errorMessage == null && stopSuccess) {
                    val finalizeResult = MediaStoreRecorderStorage.finalizeVideo(
                        applicationContext,
                        outputSession.uri,
                        outputSession.displayName
                    )
                    finalizeResult.onSuccess { validation ->
                        _recordingState.value = RecordingState.Saved(
                            uri = outputSession.uri,
                            displayName = outputSession.displayName,
                            sizeBytes = validation.sizeBytes,
                            durationMs = validation.durationMs
                        )
                        Log.d(TAG, "Recording saved: ${outputSession.displayName}")
                    }.onFailure { err ->
                        _recordingState.value = RecordingState.Error(
                            message = err.message ?: "Failed to finalize recording.",
                            cause = err
                        )
                    }
                } else {
                    MediaStoreRecorderStorage.discardVideo(applicationContext, outputSession.uri)
                    val finalError = errorMessage ?: "Recording stopped with errors."
                    _recordingState.value = RecordingState.Error(finalError)
                }
            } else {
                _recordingState.value = if (errorMessage != null) {
                    RecordingState.Error(errorMessage)
                } else {
                    RecordingState.Idle
                }
            }

            currentOutputSession = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupResources() {
        try { recordingSession = null } catch (e: Exception) { /* no-op */ }
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup MediaProjection error: ${e.message}")
        }
        mediaProjection = null
        try {
            currentOutputSession?.parcelFileDescriptor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup PFD close error: ${e.message}")
        }
        Log.d(TAG, "Emergency cleanup complete. Single-use token discarded.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = Constants.ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, getString(R.string.action_stop), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(durationMs: Long) {
        val text = "Recording: ${durationMs.formatDuration()}"
        val notification = buildNotification(text)
        getSystemService(NotificationManager::class.java)?.notify(Constants.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ScreenRecordingService destroyed.")
        serviceScope.cancel()
        cleanupResources()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
