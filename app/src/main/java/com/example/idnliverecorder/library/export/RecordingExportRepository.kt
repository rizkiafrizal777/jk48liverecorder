package com.example.idnliverecorder.library.export

import android.content.Context
import com.example.idnliverecorder.database.ExportTask
import com.example.idnliverecorder.database.IDNRecorderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for persisting and tracking video export tasks (Phase 7).
 */
class RecordingExportRepository(context: Context) {

    private val exportTaskDao = IDNRecorderDatabase.getInstance(context).exportTaskDao()

    val allTasksFlow: Flow<List<ExportTask>> = exportTaskDao.getAllTasksFlow()
    val activeTasksFlow: Flow<List<ExportTask>> = exportTaskDao.getActiveTasksFlow()

    fun getRecentExportsFlow(limit: Int = 10): Flow<List<ExportTask>> {
        return exportTaskDao.getRecentExportsFlow(limit)
    }

    suspend fun createTask(
        sourceUri: String,
        destinationUri: String,
        displayName: String,
        totalBytes: Long
    ): Long = withContext(Dispatchers.IO) {
        val task = ExportTask(
            sourceUri = sourceUri,
            destinationUri = destinationUri,
            displayName = displayName,
            totalBytes = totalBytes,
            copiedBytes = 0L,
            progressPercent = 0,
            status = ExportTask.STATUS_QUEUED
        )
        exportTaskDao.insertTask(task)
    }

    suspend fun getTaskById(id: Long): ExportTask? = withContext(Dispatchers.IO) {
        exportTaskDao.getTaskById(id)
    }

    suspend fun getActiveTaskBySourceUri(sourceUri: String): ExportTask? = withContext(Dispatchers.IO) {
        exportTaskDao.getActiveTaskBySourceUri(sourceUri)
    }

    suspend fun updateProgress(id: Long, copiedBytes: Long, percent: Int) = withContext(Dispatchers.IO) {
        exportTaskDao.updateProgress(id, copiedBytes, percent)
    }

    suspend fun markRunning(id: Long) = withContext(Dispatchers.IO) {
        exportTaskDao.updateTaskStatus(id, ExportTask.STATUS_RUNNING, null, null)
    }

    suspend fun markCompleted(id: Long) = withContext(Dispatchers.IO) {
        exportTaskDao.updateTaskStatus(id, ExportTask.STATUS_COMPLETED, null, System.currentTimeMillis())
    }

    suspend fun markCancelled(id: Long) = withContext(Dispatchers.IO) {
        exportTaskDao.updateTaskStatus(id, ExportTask.STATUS_CANCELLED, "Dibatalkan oleh pengguna", System.currentTimeMillis())
    }

    suspend fun markFailed(id: Long, error: String) = withContext(Dispatchers.IO) {
        exportTaskDao.updateTaskStatus(id, ExportTask.STATUS_FAILED, error, System.currentTimeMillis())
    }
}

