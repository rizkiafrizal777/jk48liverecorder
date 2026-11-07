package com.example.idnliverecorder.recording.source

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.example.idnliverecorder.recording.RecordingConfig

/**
 * OfficialStreamProvider (Integration Stub)
 *
 * Dedicated provider for a future officially authorized IDN Live stream integration.
 *
 * SAFETY & POLICY RULES:
 * - No private API reverse-engineering.
 * - No private stream URL or credential extraction.
 * - No unofficial network traffic interception.
 * - In the absence of an authorized partner SDK or API agreement from IDN,
 *   this provider explicitly returns UNSUPPORTED_OFFICIAL_SOURCE.
 */
class OfficialStreamProvider(
    private val isAuthorizedIntegrationActive: Boolean = false
) : LiveSourceProvider {

    companion object {
        private const val TAG = "OfficialStreamProvider"
        const val CODE_UNSUPPORTED = "UNSUPPORTED_OFFICIAL_SOURCE"
        const val MESSAGE_UNSUPPORTED = "Official live stream integration is not available."
    }

    override val type: LiveSourceType = LiveSourceType.OfficialLiveStream

    override val capabilities: LiveSourceCapabilities = LiveSourceCapabilities.forOfficialStream()

    override val isVideoSourceActive: Boolean = false
    override val isAudioSourceActive: Boolean = false

    override fun prepare(
        context: Context,
        streamerName: String?,
        streamerId: String?
    ): LiveSourceResult {
        Log.d(TAG, "prepare() called for streamer: $streamerName (id: $streamerId)")

        if (!isAuthorizedIntegrationActive) {
            Log.w(TAG, "Official integration is not configured or authorized.")
            return LiveSourceResult.UnsupportedOfficialSource(
                code = CODE_UNSUPPORTED,
                message = MESSAGE_UNSUPPORTED
            )
        }

        // When official partner SDK is supplied, initialization occurs here
        return LiveSourceResult.Ready(capabilities)
    }

    override fun startVideoSource(
        mediaProjection: MediaProjection?,
        config: RecordingConfig,
        targetSurface: Surface,
        mainHandler: Handler
    ): Boolean {
        Log.w(TAG, "Cannot start official stream: $MESSAGE_UNSUPPORTED")
        return false
    }

    override fun stop() {
        Log.d(TAG, "OfficialStreamProvider stopped.")
    }

    override fun release() {
        Log.d(TAG, "OfficialStreamProvider released.")
    }
}

