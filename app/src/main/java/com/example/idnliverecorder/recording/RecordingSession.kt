package com.example.idnliverecorder.recording

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.example.idnliverecorder.audio.AudioCaptureManager
import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.recording.source.LiveSourceProvider
import com.example.idnliverecorder.recording.source.ScreenCaptureProvider
import com.example.idnliverecorder.storage.MediaStoreRecorderStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates the full MediaCodec + AudioRecord + MediaMuxer recording pipeline.
 *
 * Integrates with [LiveSourceProvider] to decouple video sourcing
 * from encoding and muxing.
 *
 * Monotonic timestamp synchronization:
 * presentationTimeUs = (System.nanoTime() - sessionStartNanos) / 1000
 *
 * Audio and Video pipelines are completely independent:
 * If internal audio is unavailable, permission denied, or restricted by the app,
 * the session smoothly proceeds in Video-Only mode.
 */
class RecordingSession(
    private val context: Context,
    private val config: RecordingConfig,
    private val mediaProjection: MediaProjection?,
    private val outputSession: MediaStoreRecorderStorage.OutputSession,
    private val hasRecordAudioPermission: Boolean,
    val sourceProvider: LiveSourceProvider = ScreenCaptureProvider(),
    private val streamerInfo: DetectedLiveItem? = null,
    private val onAudioStatusChanged: (AudioCaptureStatus) -> Unit
) {
    companion object {
        private const val TAG = "RecordingSession"
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val videoEncoder = VideoEncoder(config)
    private val audioCaptureManager = AudioCaptureManager()
    private var audioEncoderInstance: AudioEncoder? = null
    private var muxerManager: MediaMuxerManager? = null

    private var sessionStartNanos: Long = 0L
    private var audioJob: Job? = null
    private var videoJob: Job? = null
    @Volatile private var actualAudioStatus: AudioCaptureStatus = AudioCaptureStatus.NotStarted

    val sessionModel: RecordingSessionModel = RecordingSessionModel.fromDetection(
        item = streamerInfo,
        provider = sourceProvider.type
    )

    /**
     * Prepare and start the recording session.
     * @return The VirtualDisplay input Surface.
     */
    fun start(mainHandler: Handler): Surface {
        Log.d(TAG, "RecordingSession.start() with provider: ${sourceProvider.type.displayName}")
        sessionStartNanos = System.nanoTime()

        // Prepare provider
        val prepResult = sourceProvider.prepare(
            context = context,
            streamerName = streamerInfo?.streamerName,
            streamerId = null
        )
        Log.d(TAG, "Source provider preparation result: $prepResult")

        // Determine if we can/should use audio
        val attemptAudio = config.includeAudio && hasRecordAudioPermission

        // Create muxer (includeAudio determines how many tracks it waits for)
        muxerManager = MediaMuxerManager(
            pfd = outputSession.parcelFileDescriptor,
            includeAudio = attemptAudio
        )

        // --- Prepare and start video encoder ---
        videoEncoder.prepare()
        val inputSurface = videoEncoder.getInputSurface()
        videoEncoder.start()

        // --- Start video source ---
        val videoStarted = sourceProvider.startVideoSource(
            mediaProjection = mediaProjection,
            config = config,
            targetSurface = inputSurface,
            mainHandler = mainHandler
        )

        if (!videoStarted) {
            Log.e(TAG, "Failed to start video source provider: ${sourceProvider.type.displayName}")
        }

        // --- Audio setup (Independent from video) ---
        if (attemptAudio && mediaProjection != null) {
            onAudioStatusChanged(AudioCaptureStatus.Initializing)
            val prepareStatus = audioCaptureManager.prepare(mediaProjection)

            if (prepareStatus == AudioCaptureStatus.Initializing) {
                val aEncoder = AudioEncoder(
                    sampleRate = audioCaptureManager.sampleRate,
                    channelCount = audioCaptureManager.channelCount,
                    bitrate = config.audioBitrate
                )
                aEncoder.prepare()
                aEncoder.start()
                audioEncoderInstance = aEncoder

                val captureStarted = audioCaptureManager.startCapture()
                if (captureStarted) {
                    actualAudioStatus = AudioCaptureStatus.Active
                    onAudioStatusChanged(AudioCaptureStatus.Active)
                    if (sourceProvider is ScreenCaptureProvider) {
                        sourceProvider.markAudioActive(true)
                    }
                    startAudioCaptureLoop(aEncoder)
                } else {
                    Log.w(TAG, "AudioRecord.startRecording() failed — switching to VideoOnly")
                    aEncoder.signalEOS()
                    drainAudioEncoderToCompletion(aEncoder)
                    actualAudioStatus = AudioCaptureStatus.VideoOnly
                    onAudioStatusChanged(AudioCaptureStatus.VideoOnly)
                }
            } else {
                Log.w(TAG, "Audio prepare failed with status=$prepareStatus — switching to VideoOnly")
                actualAudioStatus = prepareStatus
                onAudioStatusChanged(AudioCaptureStatus.VideoOnly)
                muxerManager?.stopAndRelease()
                muxerManager = MediaMuxerManager(
                    pfd = outputSession.parcelFileDescriptor,
                    includeAudio = false
                )
            }
        } else {
            if (!hasRecordAudioPermission && config.includeAudio) {
                actualAudioStatus = AudioCaptureStatus.PermissionDenied
                onAudioStatusChanged(AudioCaptureStatus.PermissionDenied)
            } else {
                actualAudioStatus = AudioCaptureStatus.VideoOnly
                onAudioStatusChanged(AudioCaptureStatus.VideoOnly)
            }
        }

        // --- Start video drain loop ---
        startVideoDrainLoop()

        return inputSurface
    }

    private fun currentPresentationTimeUs(): Long =
        (System.nanoTime() - sessionStartNanos) / 1000L

    private fun startAudioCaptureLoop(aEncoder: AudioEncoder) {
        audioJob = sessionScope.launch {
            val buffer = audioCaptureManager.createReadBuffer()
            Log.d(TAG, "Audio capture loop started.")

            while (isActive && audioCaptureManager.isActive()) {
                val bytesRead = audioCaptureManager.read(buffer)
                when {
                    bytesRead > 0 -> {
                        val pts = currentPresentationTimeUs()
                        aEncoder.encode(buffer.copyOf(bytesRead), pts)
                        aEncoder.drainEncodedData(
                            onFormatChanged = { fmt -> muxerManager?.addAudioTrack(fmt) },
                            onEncodedData = { data, info -> muxerManager?.writeAudioSample(data, info) }
                        )
                    }
                    bytesRead < 0 -> {
                        Log.e(TAG, "AudioRecord read error code: $bytesRead")
                        break
                    }
                }
            }

            Log.d(TAG, "Audio capture loop ended — signaling EOS.")
            aEncoder.signalEOS()
            drainAudioEncoderToCompletion(aEncoder)
        }
    }

    private fun drainAudioEncoderToCompletion(aEncoder: AudioEncoder) {
        var draining = true
        while (draining) {
            draining = aEncoder.drainEncodedData(
                onFormatChanged = { fmt -> muxerManager?.addAudioTrack(fmt) },
                onEncodedData = { data, info -> muxerManager?.writeAudioSample(data, info) }
            )
        }
    }

    private fun startVideoDrainLoop() {
        videoJob = sessionScope.launch {
            Log.d(TAG, "Video drain loop started.")
            var draining = true
            while (isActive && draining) {
                draining = videoEncoder.drainEncodedData(
                    onFormatChanged = { fmt -> muxerManager?.addVideoTrack(fmt) },
                    onEncodedData = { data, info -> muxerManager?.writeVideoSample(data, info) }
                )
            }
            Log.d(TAG, "Video drain loop ended.")
        }
    }

    /**
     * Stop the recording session. Blocks until all buffers flushed and muxer closed.
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        Log.d(TAG, "RecordingSession.stop()")

        // 1. Signal video EOS
        videoEncoder.signalEOS()

        // 2. Stop audio capture
        audioCaptureManager.stopAndRelease()

        // 3. Wait for coroutines to finish
        try { audioJob?.join() } catch (e: Exception) { Log.e(TAG, "audioJob join error: ${e.message}") }
        try { videoJob?.join() } catch (e: Exception) { Log.e(TAG, "videoJob join error: ${e.message}") }

        // 4. Release audio encoder
        try { audioEncoderInstance?.release() } catch (e: Exception) { Log.e(TAG, "audioEncoder release error: ${e.message}") }

        // 5. Stop muxer
        muxerManager?.stopAndRelease()

        // 6. Release video encoder
        try { videoEncoder.release() } catch (e: Exception) { Log.e(TAG, "videoEncoder release error: ${e.message}") }

        // 7. Release source provider
        try { sourceProvider.stop() } catch (e: Exception) { Log.e(TAG, "sourceProvider stop error: ${e.message}") }
        try { sourceProvider.release() } catch (e: Exception) { Log.e(TAG, "sourceProvider release error: ${e.message}") }

        actualAudioStatus = AudioCaptureStatus.Stopped
        onAudioStatusChanged(AudioCaptureStatus.Stopped)
        Log.d(TAG, "RecordingSession fully stopped.")
    }

    fun getAudioStatus(): AudioCaptureStatus = actualAudioStatus
}
