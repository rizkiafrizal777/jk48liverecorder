package com.example.idnliverecorder.recording.source

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Handler
import android.view.Surface
import com.example.idnliverecorder.recording.RecordingConfig

/**
 * Interface representing a video/audio source provider for the recording engine.
 * Decouples video acquisition from the MediaCodec encoders and MediaMuxer pipeline.
 */
interface LiveSourceProvider {
    val type: LiveSourceType
    val capabilities: LiveSourceCapabilities
    val isVideoSourceActive: Boolean
    val isAudioSourceActive: Boolean

    /**
     * Prepares the source provider before starting capture.
     * @param context Application context.
     * @param streamerName Optional detected streamer name.
     * @param streamerId Optional official streamer ID (if officially documented).
     */
    fun prepare(
        context: Context,
        streamerName: String?,
        streamerId: String?
    ): LiveSourceResult

    /**
     * Starts the video source pipeline, outputting frames to [targetSurface].
     * @param mediaProjection Active MediaProjection token (if required by capabilities).
     * @param config Target recording configuration (resolution, framerate, etc.).
     * @param targetSurface Surface created by VideoEncoder.
     * @param mainHandler Looper handler for system callbacks.
     */
    fun startVideoSource(
        mediaProjection: MediaProjection?,
        config: RecordingConfig,
        targetSurface: Surface,
        mainHandler: Handler
    ): Boolean

    /**
     * Stops the source pipeline.
     */
    fun stop()

    /**
     * Releases all hardware and virtual resources.
     */
    fun release()
}

