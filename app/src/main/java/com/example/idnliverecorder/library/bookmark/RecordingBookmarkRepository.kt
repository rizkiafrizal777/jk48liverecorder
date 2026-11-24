package com.example.idnliverecorder.library.bookmark

import android.content.Context
import com.example.idnliverecorder.database.IDNRecorderDatabase
import com.example.idnliverecorder.database.RecordingBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for managing video playback bookmarks (Phase 7).
 */
class RecordingBookmarkRepository(context: Context) {

    private val bookmarkDao = IDNRecorderDatabase.getInstance(context).bookmarkDao()

    fun getBookmarksForRecordingFlow(recordingId: Long): Flow<List<RecordingBookmark>> {
        return bookmarkDao.getBookmarksForRecordingFlow(recordingId)
    }

    suspend fun getBookmarksForRecording(recordingId: Long): List<RecordingBookmark> = withContext(Dispatchers.IO) {
        bookmarkDao.getBookmarksForRecording(recordingId)
    }

    suspend fun addBookmark(
        recordingId: Long,
        recordingUri: String,
        timestampMs: Long,
        title: String,
        note: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val sanitizedTitle = title.trim().ifBlank { "Bookmark @ ${formatTimestamp(timestampMs)}" }
        val bookmark = RecordingBookmark(
            recordingId = recordingId,
            recordingUri = recordingUri,
            timestampMs = timestampMs,
            title = sanitizedTitle,
            note = note?.trim()?.ifBlank { null }
        )
        bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun updateBookmark(bookmark: RecordingBookmark): Unit = withContext(Dispatchers.IO) {
        bookmarkDao.updateBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmark: RecordingBookmark): Unit = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    suspend fun deleteBookmarksForRecording(recordingId: Long): Unit = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmarksForRecording(recordingId)
    }

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSeconds = ms / 1000
            val seconds = totalSeconds % 60
            val minutes = (totalSeconds / 60) % 60
            val hours = totalSeconds / 3600
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

