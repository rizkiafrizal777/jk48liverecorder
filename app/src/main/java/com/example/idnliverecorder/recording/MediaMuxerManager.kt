package com.example.idnliverecorder.recording

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manages the MediaMuxer lifecycle for combining encoded H.264 video and AAC audio
 * into a single MP4 container.
 *
 * The muxer can only start AFTER both video and audio tracks have been added
 * (or just video track if includeAudio is false).
 *
 * Thread safety: writeSample() is synchronized via a ReentrantLock.
 */
class MediaMuxerManager(
    pfd: ParcelFileDescriptor,
    private val includeAudio: Boolean = true
) {
    companion object {
        private const val TAG = "MediaMuxerManager"
        private const val UNSET_TRACK = -1
    }

    private val muxer: MediaMuxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val lock = ReentrantLock()

    private var videoTrackIndex: Int = UNSET_TRACK
    private var audioTrackIndex: Int = UNSET_TRACK
    private var isMuxerStarted: Boolean = false
    private var isStopped: Boolean = false

    /**
     * Add the video track format.
     */
    fun addVideoTrack(format: MediaFormat) {
        lock.withLock {
            if (videoTrackIndex != UNSET_TRACK) {
                Log.w(TAG, "Video track already added, ignoring duplicate.")
                return
            }
            videoTrackIndex = muxer.addTrack(format)
            Log.d(TAG, "Video track added: index=$videoTrackIndex")
            tryStartMuxer()
        }
    }

    /**
     * Add the audio track format.
     */
    fun addAudioTrack(format: MediaFormat) {
        lock.withLock {
            if (audioTrackIndex != UNSET_TRACK) {
                Log.w(TAG, "Audio track already added, ignoring duplicate.")
                return
            }
            audioTrackIndex = muxer.addTrack(format)
            Log.d(TAG, "Audio track added: index=$audioTrackIndex")
            tryStartMuxer()
        }
    }

    private fun tryStartMuxer() {
        val videoReady = videoTrackIndex != UNSET_TRACK
        val audioReady = !includeAudio || audioTrackIndex != UNSET_TRACK

        if (videoReady && audioReady && !isMuxerStarted) {
            muxer.start()
            isMuxerStarted = true
            Log.d(TAG, "MediaMuxer started. videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex")
        }
    }

    /**
     * Write an encoded sample to the muxer.
     */
    fun writeSample(trackIndex: Int, data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        lock.withLock {
            if (!isMuxerStarted || isStopped) return
            if (trackIndex == UNSET_TRACK) return
            try {
                val buffer = java.nio.ByteBuffer.wrap(data)
                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            } catch (e: Exception) {
                Log.e(TAG, "writeSampleData error: ${e.message}")
            }
        }
    }

    fun writeVideoSample(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        writeSample(videoTrackIndex, data, bufferInfo)
    }

    fun writeAudioSample(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        writeSample(audioTrackIndex, data, bufferInfo)
    }

    fun isStarted(): Boolean = isMuxerStarted && !isStopped

    /**
     * Stop and release the muxer.
     */
    fun stopAndRelease() {
        lock.withLock {
            if (isStopped) return
            isStopped = true
            if (isMuxerStarted) {
                try {
                    muxer.stop()
                    Log.d(TAG, "MediaMuxer stopped.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping MediaMuxer: ${e.message}")
                }
            }
            try {
                muxer.release()
                Log.d(TAG, "MediaMuxer released.")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaMuxer: ${e.message}")
            }
        }
    }
}

