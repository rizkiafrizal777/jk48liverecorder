package com.example.idnliverecorder.recording

import android.net.Uri
import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.recording.source.LiveSourceType
import java.util.UUID

/**
 * Status of an active or completed recording session.
 */
enum class SessionStatus {
    Preparing,
    Active,
    Stopping,
    Saved,
    Error,
    Cancelled
}

/**
 * Comprehensive recording session model representing state, streamer metadata,
 * source provider, and audio/video pipeline statuses.
 */
data class RecordingSessionModel(
    val sessionId: String = UUID.randomUUID().toString(),
    val streamerName: String? = null,
    val streamerId: String? = null,
    val detectionTimestamp: Long? = null,
    val selectedProvider: LiveSourceType = LiveSourceType.FullScreenRecording,
    val isVideoActive: Boolean = false,
    val audioStatus: AudioCaptureStatus = AudioCaptureStatus.NotStarted,
    val durationMs: Long = 0L,
    val outputUri: Uri? = null,
    val status: SessionStatus = SessionStatus.Preparing
) {
    companion object {
        fun fromDetection(
            item: DetectedLiveItem?,
            provider: LiveSourceType = LiveSourceType.FullScreenRecording
        ): RecordingSessionModel {
            return RecordingSessionModel(
                sessionId = UUID.randomUUID().toString(),
                streamerName = item?.streamerName,
                streamerId = null, // Never assume streamer name is a valid IDN stream identifier
                detectionTimestamp = item?.detectedAtMs,
                selectedProvider = provider,
                status = SessionStatus.Preparing
            )
        }
    }
}
