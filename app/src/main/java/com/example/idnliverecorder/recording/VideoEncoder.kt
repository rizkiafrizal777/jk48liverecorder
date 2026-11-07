package com.example.idnliverecorder.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/**
 * Wraps MediaCodec for H.264 video encoding from a Surface input.
 * The encoded Surface is used as the VirtualDisplay target.
 */
class VideoEncoder(private val config: RecordingConfig) {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val VIDEO_MIME = "video/avc" // H.264
        private const val TIMEOUT_US = 10_000L
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var isStarted = false

    /**
     * Prepares the MediaCodec video encoder. Must call before [getInputSurface] or [start].
     * @throws IllegalStateException if encoder setup fails.
     */
    fun prepare() {
        Log.d(TAG, "Preparing H.264 video encoder: ${config.width}x${config.height} @ ${config.frameRate}fps, bitrate=${config.bitRate}")

        val format = MediaFormat.createVideoFormat(VIDEO_MIME, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
        }

        val codec = MediaCodec.createEncoderByType(VIDEO_MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        mediaCodec = codec
        Log.d(TAG, "VideoEncoder prepared successfully.")
    }

    /**
     * Returns the Surface to use as the VirtualDisplay output target.
     * Must call [prepare] first.
     */
    fun getInputSurface(): Surface {
        return inputSurface ?: throw IllegalStateException("VideoEncoder not prepared. Call prepare() first.")
    }

    /** Start the encoder. Must call after [prepare] and before writing frames. */
    fun start() {
        mediaCodec?.start()
        isStarted = true
        Log.d(TAG, "VideoEncoder started.")
    }

    /**
     * Drains encoded data from the encoder output buffers.
     * Calls [onFormatChanged] once when MediaFormat is available (use to add muxer track).
     * Calls [onEncodedData] for each encoded buffer (use to write to muxer).
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
                    return true // No output yet — keep draining later
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "VideoEncoder output format changed: ${codec.outputFormat}")
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
                        Log.d(TAG, "VideoEncoder EOS reached.")
                        return false
                    }
                }
                else -> {
                    Log.w(TAG, "VideoEncoder unexpected output index: $outputIndex")
                }
            }
        }
    }

    /** Signal end-of-stream to the encoder surface. */
    fun signalEOS() {
        try {
            mediaCodec?.signalEndOfInputStream()
            Log.d(TAG, "VideoEncoder EOS signaled.")
        } catch (e: Exception) {
            Log.e(TAG, "Error signaling EOS: ${e.message}")
        }
    }

    /** Release all encoder resources. Safe to call multiple times. */
    fun release() {
        isStarted = false
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VideoEncoder: ${e.message}")
        }
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VideoEncoder: ${e.message}")
        }
        mediaCodec = null
        try {
            inputSurface?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing input Surface: ${e.message}")
        }
        inputSurface = null
        Log.d(TAG, "VideoEncoder released.")
    }
}

