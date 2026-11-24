package com.example.idnliverecorder.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for intensive video probe metadata (bitrate, framerate, precise MIME type) (Phase 7).
 */
@Entity(tableName = "recording_metadata_cache")
data class RecordingMetadataCache(
    @PrimaryKey
    val recordingId: Long,
    val recordingUri: String,
    val frameRate: Float? = null,
    val bitrate: Long? = null,
    val mimeType: String? = null,
    val lastChecked: Long = System.currentTimeMillis()
)

