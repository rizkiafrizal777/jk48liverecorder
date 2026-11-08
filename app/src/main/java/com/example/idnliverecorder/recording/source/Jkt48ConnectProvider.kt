package com.example.idnliverecorder.recording.source

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.example.idnliverecorder.monitoring.jkt48.Jkt48ConnectRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
import com.example.idnliverecorder.recording.RecordingConfig
import com.example.idnliverecorder.recording.validation.StreamSourceValidator

/**
 * JKT48Connect Live Source Provider (Phase 5).
 *
 * Implements [LiveSourceProvider] specifically for JKT48 live streams
 * discovered via JKT48Connect API.
 *
 * SAFETY & POLICY:
 * - Operates only on official metadata provided by JKT48Connect API.
 * - Does not intercept private traffic or extract auth tokens.
 */
class Jkt48ConnectProvider(
    private val repository: Jkt48ConnectRepository? = null,
    private val validator: StreamSourceValidator = StreamSourceValidator()
) : LiveSourceProvider {

    companion object {
        private const val TAG = "Jkt48ConnectProvider"
    }

    override val type: LiveSourceType = LiveSourceType.OfficialLiveStream

    override val capabilities: LiveSourceCapabilities = LiveSourceCapabilities(
        type = LiveSourceType.OfficialLiveStream,
        isAvailableOnDevice = true,
        minSdk = 29,
        requiresMediaProjection = false,
        supportsBackgroundRecording = true,
        recordsOtherApplications = false,
        recordsUiElements = false,
        displayName = "JKT48Connect Live Stream",
        description = "Direct stream capture for JKT48 members via JKT48Connect integration.",
        availabilityNotice = "Direct stream integration requires authorized stream source."
    )

    override var isVideoSourceActive: Boolean = false
        private set

    override var isAudioSourceActive: Boolean = false
        private set

    private var activeMember: Jkt48LiveMember? = null
    private var verifiedStreamUrl: String? = null

    override fun prepare(
        context: Context,
        streamerName: String?,
        streamerId: String?
    ): LiveSourceResult {
        Log.d(TAG, "Preparing Jkt48ConnectProvider for streamer: $streamerName (id: $streamerId)")
        // Provider is ready to be configured with stream URL
        return LiveSourceResult.Ready(capabilities)
    }

    fun configureTargetMember(member: Jkt48LiveMember, streamUrl: String?) {
        activeMember = member
        verifiedStreamUrl = streamUrl
    }

    override fun startVideoSource(
        mediaProjection: MediaProjection?,
        config: RecordingConfig,
        targetSurface: Surface,
        mainHandler: Handler
    ): Boolean {
        // Direct stream recording does not output to MediaProjection Surface
        // It uses DirectStreamRecordingEngine instead.
        Log.d(TAG, "startVideoSource called on direct network provider.")
        isVideoSourceActive = true
        isAudioSourceActive = true
        return true
    }

    override fun stop() {
        Log.d(TAG, "Stopping Jkt48ConnectProvider.")
        isVideoSourceActive = false
        isAudioSourceActive = false
    }

    override fun release() {
        Log.d(TAG, "Releasing Jkt48ConnectProvider.")
        activeMember = null
        verifiedStreamUrl = null
        stop()
    }
}
