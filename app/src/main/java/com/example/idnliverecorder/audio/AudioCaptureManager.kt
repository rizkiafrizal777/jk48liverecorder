package com.example.idnliverecorder.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import com.example.idnliverecorder.recording.AudioCaptureStatus

/**
 * Manages internal audio capture via Android AudioPlaybackCaptureConfiguration API.
 *
 * IMPORTANT: This class NEVER uses microphone audio.
 * Audio is captured from app playback only, using the MediaProjection token.
 * If the target app restricts capture, reads return silence (zeros).
 */
class AudioCaptureManager {

    companion object {
        private const val TAG = "AudioCaptureManager"
        private val PREFERRED_SAMPLE_RATES = intArrayOf(44100, 48000)
        private val PREFERRED_CHANNEL_CONFIGS = intArrayOf(
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.CHANNEL_IN_MONO
        )
    }

    private var audioRecord: AudioRecord? = null
    private var actualSampleRate: Int = 44100
    private var actualChannelConfig: Int = AudioFormat.CHANNEL_IN_STEREO
    private var actualChannelCount: Int = 2
    private var bufferSizeBytes: Int = 0
    private var isCapturing: Boolean = false

    val sampleRate: Int get() = actualSampleRate
    val channelCount: Int get() = actualChannelCount

    /**
     * Initialize AudioRecord using AudioPlaybackCaptureConfiguration.
     * Returns the resulting status.
     * Must be called before [startCapture].
     *
     * @param mediaProjection A valid, active MediaProjection token (not consumed/stopped).
     * @return AudioCaptureStatus indicating success or specific failure reason.
     */
    fun prepare(mediaProjection: MediaProjection): AudioCaptureStatus {
        Log.d(TAG, "Preparing AudioPlaybackCaptureConfiguration...")

        val captureConfig = try {
            AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build AudioPlaybackCaptureConfiguration: ${e.message}", e)
            return AudioCaptureStatus.InitFailed
        }

        // Try preferred sample rates and channel configurations
        for (sampleRate in PREFERRED_SAMPLE_RATES) {
            for (channelConfig in PREFERRED_CHANNEL_CONFIGS) {
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                if (minBuf <= 0 || minBuf == AudioRecord.ERROR_BAD_VALUE) {
                    Log.w(TAG, "Unsupported config: sampleRate=$sampleRate, channelConfig=$channelConfig")
                    continue
                }

                val bufSize = minBuf * 2

                val record = try {
                    AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(bufSize)
                        .build()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord.Builder failed for sampleRate=$sampleRate: ${e.message}")
                    continue
                }

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord not initialized for sampleRate=$sampleRate, releasing...")
                    record.release()
                    continue
                }

                // Success — store the working configuration
                audioRecord = record
                actualSampleRate = sampleRate
                actualChannelConfig = channelConfig
                actualChannelCount = if (channelConfig == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
                bufferSizeBytes = bufSize

                Log.d(TAG, "AudioRecord initialized: sampleRate=$sampleRate, channels=$actualChannelCount, bufSize=$bufSize")
                return AudioCaptureStatus.Initializing
            }
        }

        Log.e(TAG, "All AudioRecord configurations failed — no supported format found.")
        return AudioCaptureStatus.InitFailed
    }

    /**
     * Start audio capture. Must call [prepare] first.
     * @return true if started successfully.
     */
    fun startCapture(): Boolean {
        val record = audioRecord ?: run {
            Log.e(TAG, "startCapture called before prepare()")
            return false
        }
        return try {
            record.startRecording()
            isCapturing = record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            Log.d(TAG, "AudioRecord.startRecording() — isCapturing=$isCapturing")
            isCapturing
        } catch (e: Exception) {
            Log.e(TAG, "startRecording() exception: ${e.message}", e)
            false
        }
    }

    /**
     * Read PCM audio bytes from the capture buffer.
     * Returns the number of bytes read, or a negative AudioRecord error code.
     * Returns 0 if capture is stopped.
     */
    fun read(buffer: ByteArray): Int {
        if (!isCapturing) return 0
        return audioRecord?.read(buffer, 0, buffer.size) ?: 0
    }

    /**
     * Returns a byte buffer sized at the internal buffer size (suitable for single-pass reads).
     */
    fun createReadBuffer(): ByteArray = ByteArray(bufferSizeBytes.coerceAtLeast(4096))

    /**
     * Stop capture and release all AudioRecord resources.
     */
    fun stopAndRelease() {
        isCapturing = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
        Log.d(TAG, "AudioCaptureManager: AudioRecord stopped and released.")
    }

    fun isInitialized(): Boolean = audioRecord != null
    fun isActive(): Boolean = isCapturing
}
