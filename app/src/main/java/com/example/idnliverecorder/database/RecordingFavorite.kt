package com.example.idnliverecorder.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity tracking user favorite recordings with persistent state across app restarts (Phase 7).
 */
@Entity(tableName = "recording_favorites")
data class RecordingFavorite(
    @PrimaryKey
    val recordingId: Long,
    val recordingUri: String,
    val displayName: String,
    val isFavorite: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

