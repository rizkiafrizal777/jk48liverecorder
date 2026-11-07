package com.example.idnliverecorder.recording.source

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Handler
import android.view.Surface
import com.example.idnliverecorder.recording.RecordingConfig

/**
 * Fallback provider when an unsupported source mode is requested.
 */
class UnsupportedSourceProvider(
    private val failureReason: String = "Requested source provider is unsupported."
) : LiveSourceProvider {

    override val type: LiveSourceType = LiveSourceType.Unsupported
    override val capabilities: LiveSourceCapabilities = LiveSourceCapabilities.forUnsupported()
    override val isVideoSourceActive: Boolean = false
    override val isAudioSourceActive: Boolean = false

    override fun prepare(
        context: Context,
        streamerName: String?,
        streamerId: String?
    ): LiveSourceResult {
        return LiveSourceResult.DeviceNotSupported(failureReason)
    }

    override fun startVideoSource(
        mediaProjection: MediaProjection?,
        config: RecordingConfig,
        targetSurface: Surface,
        mainHandler: Handler
    ): Boolean = false

    override fun stop() {}
    override fun release() {}
}

