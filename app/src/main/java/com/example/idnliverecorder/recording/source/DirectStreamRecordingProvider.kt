package com.example.idnliverecorder.recording.source

import android.content.Context
import android.util.Log
import com.example.idnliverecorder.recording.direct.DirectStreamRecordingEngine
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import com.example.idnliverecorder.recording.validation.StreamSourceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DirectStreamRecordingProvider (Phase 5)
 *
 * Implements [RecordingSourceProvider] for direct network live stream recording.
 * - Does NOT require MediaProjection.
 * - Supports true background recording via DirectRecordingService.
 * - Strictly checks authorization before starting.
 */
class DirectStreamRecordingProvider(
    private val validator: StreamSourceValidator = StreamSourceValidator()
) : RecordingSourceProvider {

    companion object {
        private const val TAG = "DirectStreamProvider"
    }

    override val id: String = "direct_stream"
    override val displayName: String = "Direct Live Stream Recording"
    override val requiresMediaProjection: Boolean = false
    override val supportsBackgroundRecording: Boolean = true

    private val _currentStatus = MutableStateFlow(DirectStreamStatus.SOURCE_UNAVAILABLE)
    override val currentStatus: StateFlow<DirectStreamStatus> = _currentStatus.asStateFlow()

    private var activeEngine: DirectStreamRecordingEngine? = null
    private var streamUrl: String? = null
    private var memberName: String = "JKT48"
    private var isRecordingAuthorized: Boolean = false

    override suspend fun prepareSource(context: Context, targetInfo: Map<String, Any?>): Boolean {
        streamUrl = targetInfo["streamUrl"] as? String
        memberName = (targetInfo["memberName"] as? String) ?: "JKT48"
        isRecordingAuthorized = (targetInfo["isRecordingAuthorized"] as? Boolean) ?: false

        Log.d(TAG, "prepareSource: member=$memberName, authorized=$isRecordingAuthorized")

        val validation = validator.validate(
            url = streamUrl,
            isRecordingAuthorized = isRecordingAuthorized
        )

        _currentStatus.value = validation.status
        return validation.canRecord
    }

    override suspend fun startCapture(): Boolean {
        if (_currentStatus.value != DirectStreamStatus.READY_TO_RECORD) {
            Log.w(TAG, "Cannot start direct capture: status is ${_currentStatus.value}")
            return false
        }

        activeEngine?.start()
        _currentStatus.value = DirectStreamStatus.RECORDING
        return true
    }

    fun bindEngine(engine: DirectStreamRecordingEngine) {
        activeEngine = engine
    }

    override suspend fun stopCapture() {
        Log.d(TAG, "stopCapture called on DirectStreamRecordingProvider.")
        _currentStatus.value = DirectStreamStatus.FINALIZING
        activeEngine?.stop()
        _currentStatus.value = DirectStreamStatus.COMPLETED
    }

    override fun release() {
        activeEngine?.release()
        activeEngine = null
        streamUrl = null
        _currentStatus.value = DirectStreamStatus.SOURCE_UNAVAILABLE
    }
}
