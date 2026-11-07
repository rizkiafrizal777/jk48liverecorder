package com.example.idnliverecorder.recording

import android.net.Uri
import com.example.idnliverecorder.recording.source.LiveSourceType

/**
 * Represents the status of internal audio capture via AudioPlaybackCaptureConfiguration.
 * No microphone is ever used.
 */
enum class AudioCaptureStatus {
    /** Audio pipeline not yet started */
    NotStarted,
    /** AudioRecord is being configured */
    Initializing,
    /** Audio is actively being captured from app playback */
    Active,
    /** No eligible playback detected (app may have no audio, or playback hasn't started) */
    NoEligiblePlayback,
    /** The source application has set allowAudioPlaybackCapture=false */
    AppRestrictsCapture,
    /** RECORD_AUDIO permission was denied */
    PermissionDenied,
    /** AudioRecord failed to initialize (unsupported config, hardware error) */
    InitFailed,
    /** Recording continues with video only — audio was silently unavailable */
    VideoOnly,
    /** Audio pipeline cleanly stopped */
    Stopped
}

sealed interface RecordingState {
    object Idle : RecordingState
    object Starting : RecordingState

    data class Recording(
        val startTimeMs: Long,
        val durationMs: Long = 0L,
        val config: RecordingConfig,
        val outputUri: Uri,
        val audioStatus: AudioCaptureStatus = AudioCaptureStatus.NotStarted,
        val sourceType: LiveSourceType = LiveSourceType.FullScreenRecording,
        val sessionModel: RecordingSessionModel? = null
    ) : RecordingState

    object Stopping : RecordingState

    data class Saved(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val durationMs: Long
    ) : RecordingState

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : RecordingState
}
