package com.example.idnliverecorder.recording.source

import android.content.Context
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * RecordingSourceProvider (Phase 5 Architecture)
 *
 * Separates recording engines into distinct providers:
 * - [DirectStreamRecordingProvider]: Captures audio & video directly from authorized network streams.
 * - [ScreenCaptureRecordingProvider]: Full screen capture via MediaProjection.
 *
 * Switching between providers is explicit; failure of one NEVER auto-switches to the other.
 */
interface RecordingSourceProvider {
    val id: String
    val displayName: String
    val requiresMediaProjection: Boolean
    val supportsBackgroundRecording: Boolean
    val currentStatus: StateFlow<DirectStreamStatus>

    /**
     * Prepares the provider with stream/target metadata.
     */
    suspend fun prepareSource(context: Context, targetInfo: Map<String, Any?>): Boolean

    /**
     * Starts the recording capture pipeline.
     */
    suspend fun startCapture(): Boolean

    /**
     * Stops the capture pipeline and finalizes the current recording.
     */
    suspend fun stopCapture()

    /**
     * Releases hardware and network resources.
     */
    fun release()
}
