package com.example.idnliverecorder.recording

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event Bridge for recording lifecycle events across direct streaming and screen recording (Phase 7-8).
 *
 * Provides a decoupled event propagation mechanism so UI ViewModels (RecordingLibraryViewModel,
 * DashboardViewModel) and notification services can immediately react when a video is finalized
 * and committed to MediaStore.
 */
object RecordingEventBridge {

    data class RecordingCompletedEvent(
        val uri: Uri,
        val memberName: String,
        val displayName: String,
        val sizeBytes: Long,
        val durationMs: Long,
        val isDirectStream: Boolean = true
    )

    private val _recordingCompletedEvents = MutableSharedFlow<RecordingCompletedEvent>(
        replay = 1,
        extraBufferCapacity = 16
    )
    val recordingCompletedEvents: SharedFlow<RecordingCompletedEvent> = _recordingCompletedEvents.asSharedFlow()

    fun notifyRecordingCompleted(event: RecordingCompletedEvent) {
        _recordingCompletedEvents.tryEmit(event)
    }
}
