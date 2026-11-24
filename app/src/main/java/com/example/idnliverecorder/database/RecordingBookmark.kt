package com.example.idnliverecorder.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a timestamped video bookmark (Phase 7).
 *
 * Uses stable MediaStore ID and URI to preserve bookmarks even if the file is renamed.
 */
@Entity(
    tableName = "recording_bookmarks",
    indices = [
        Index(value = ["recordingId"]),
        Index(value = ["recordingUri"])
    ]
)
data class RecordingBookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recordingId: Long,
    val recordingUri: String,
    val timestampMs: Long,
    val title: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

