package com.example.idnliverecorder.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity tracking background export operations, status, and history (Phase 7).
 */
@Entity(tableName = "export_tasks")
data class ExportTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sourceUri: String,
    val destinationUri: String,
    val displayName: String,
    val totalBytes: Long,
    val copiedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val status: String = STATUS_QUEUED,
    val errorMessage: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_FAILED = "FAILED"
    }

    val isTerminal: Boolean
        get() = status == STATUS_COMPLETED || status == STATUS_CANCELLED || status == STATUS_FAILED
}

