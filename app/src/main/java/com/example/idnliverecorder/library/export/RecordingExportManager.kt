package com.example.idnliverecorder.library.export

import android.content.Context
import android.net.Uri
import com.example.idnliverecorder.database.ExportTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manager interfacing between UI ViewModels and RecordingExportService (Phase 7).
 */
class RecordingExportManager(private val context: Context) {

    private val repository = RecordingExportRepository(context)

    val currentExportState: StateFlow<ExportProgressState> =
        RecordingExportService.currentExportState

    val recentExportsFlow: Flow<List<ExportTask>> = repository.getRecentExportsFlow()
    val activeTasksFlow: Flow<List<ExportTask>> = repository.activeTasksFlow

    /**
     * Checks if an export is currently running for the given source URI.
     */
    fun isExportActiveFor(sourceUri: Uri): Boolean {
        return RecordingExportService.activeExportUris.contains(sourceUri.toString())
    }

    /**
     * Initiates a background export task.
     * Prevents duplicate exports if one is already active for the source.
     */
    suspend fun startExport(
        sourceUri: Uri,
        destUri: Uri,
        displayName: String,
        totalBytes: Long
    ): Result<Long> {
        val sourceStr = sourceUri.toString()
        if (isExportActiveFor(sourceUri)) {
            return Result.failure(IllegalStateException("Ekspor untuk file ini sedang berjalan."))
        }

        return try {
            val taskId = repository.createTask(
                sourceUri = sourceStr,
                destinationUri = destUri.toString(),
                displayName = displayName,
                totalBytes = totalBytes
            )
            RecordingExportService.start(
                context = context,
                taskId = taskId,
                sourceUri = sourceUri,
                destUri = destUri,
                displayName = displayName,
                totalBytes = totalBytes
            )
            Result.success(taskId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancels the currently active export task.
     */
    fun cancelActiveExport() {
        RecordingExportService.cancel(context)
    }

    /**
     * Retries a previously failed export task.
     */
    suspend fun retryExport(task: ExportTask): Result<Long> {
        val sourceUri = Uri.parse(task.sourceUri)
        val destUri = Uri.parse(task.destinationUri)
        return startExport(sourceUri, destUri, task.displayName, task.totalBytes)
    }
}

