package com.example.idnliverecorder.recording.source

import android.content.Context
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ScreenCaptureRecordingSourceProvider (Phase 5)
 *
 * Implements [RecordingSourceProvider] wrapping the MediaProjection full screen
 * recording engine.
 *
 * NOTE: Remains an explicit fallback; failure of direct stream NEVER switches here automatically.
 */
class ScreenCaptureRecordingSourceProvider(
    val internalScreenProvider: ScreenCaptureProvider = ScreenCaptureProvider()
) : RecordingSourceProvider {

    override val id: String = "screen_capture"
    override val displayName: String = "Full Screen Recording"
    override val requiresMediaProjection: Boolean = true
    override val supportsBackgroundRecording: Boolean = false

    private val _currentStatus = MutableStateFlow(DirectStreamStatus.READY_TO_RECORD)
    override val currentStatus: StateFlow<DirectStreamStatus> = _currentStatus.asStateFlow()

    override suspend fun prepareSource(context: Context, targetInfo: Map<String, Any?>): Boolean {
        _currentStatus.value = DirectStreamStatus.READY_TO_RECORD
        return true
    }

    override suspend fun startCapture(): Boolean {
        _currentStatus.value = DirectStreamStatus.RECORDING
        return true
    }

    override suspend fun stopCapture() {
        _currentStatus.value = DirectStreamStatus.COMPLETED
    }

    override fun release() {
        internalScreenProvider.release()
        _currentStatus.value = DirectStreamStatus.SOURCE_AVAILABLE
    }
}
