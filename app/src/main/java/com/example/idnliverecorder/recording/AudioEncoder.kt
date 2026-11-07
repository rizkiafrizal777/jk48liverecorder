package com.example.idnliverecorder.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log

/**
 * Wraps MediaCodec for AAC-LC audio encoding from raw PCM input.
 *
 * Input: raw PCM 16-bit bytes from AudioCaptureManager
 * Output: encoded AAC buffers consumed by MediaMuxerManager
 */
class AudioEncoder(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bitrate: Int = 128_000
) {
    companion object {
        private const val TAG = "AudioEncoder"
        private const val AUDIO_MIME = "audio/mp4a-latm" // AAC
        private const val TIMEOUT_US = 10_000L
        private const val AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    private var mediaCodec: MediaCodec? = null
    private var isStarted = false

    /**
     * Prepares the MediaCodec AAC encoder.
     * @throws IllegalStateException if encoder setup fails.
     */
    fun prepare() {
        Log.d(TAG, "Preparing AAC encoder: sampleRate=$sampleRate, channels=$channelCount, bitrate=$bitrate")

        val format = MediaFormat.createAudioFormat(AUDIO_MIME, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec = codec
        Log.d(TAG, "AudioEncoder prepared successfully.")
    }

    /** Start the encoder. Must call after [prepare]. */
    fun start() {
        mediaCodec?.start()
        isStarted = true
        Log.d(TAG, "AudioEncoder started.")
    }

    /**
     * Feed raw PCM bytes into the encoder input.
     * @param pcmData Raw PCM 16-bit audio bytes
     * @param presentationTimeUs Monotonic presentation timestamp in microseconds
     */
    fun encode(pcmData: ByteArray, presentationTimeUs: Long) {
        val codec = mediaCodec ?: return

        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex < 0) return

        val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
        inputBuffer.clear()

        val bytesToWrite = minOf(pcmData.size, inputBuffer.capacity())
        inputBuffer.put(pcmData, 0, bytesToWrite)
        codec.queueInputBuffer(inputIndex, 0, bytesToWrite, presentationTimeUs, 0)
    }

    /**
     * Signal end-of-stream to the encoder input.
     */
    fun signalEOS() {
        val codec = mediaCodec ?: return
        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            Log.d(TAG, "AudioEncoder EOS signaled.")
        }
    }

    /**
     * Drain encoded AAC output buffers.
     * Calls [onFormatChanged] once when output format is known (to add muxer track).
     * Calls [onEncodedData] for each encoded AAC frame.
     * Returns false when EOS is reached.
     */
    fun drainEncodedData(
        onFormatChanged: (MediaFormat) -> Unit,
        onEncodedData: (ByteArray, MediaCodec.BufferInfo) -> Unit
    ): Boolean {
        val codec = mediaCodec ?: return false
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return true
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "AudioEncoder output format changed: ${codec.outputFormat}")
                    onFormatChanged(codec.outputFormat)
                }
                outputIndex >= 0 -> {
                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            val data = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.get(data)
                            onEncodedData(data, bufferInfo)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (isEos) {
                        Log.d(TAG, "AudioEncoder EOS reached.")
                        return false
                    }
                }
                else -> {
                    Log.w(TAG, "AudioEncoder unexpected output index: $outputIndex")
                }
            }
        }
    }

    /** Release all encoder resources. Safe to call multiple times. */
    fun release() {
        isStarted = false
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioEncoder: ${e.message}")
        }
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioEncoder: ${e.message}")
        }
        mediaCodec = null
        Log.d(TAG, "AudioEncoder released.")
    }
}

