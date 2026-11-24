package com.example.idnliverecorder.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM recording_bookmarks WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    fun getBookmarksForRecordingFlow(recordingId: Long): Flow<List<RecordingBookmark>>

    @Query("SELECT * FROM recording_bookmarks WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    suspend fun getBookmarksForRecording(recordingId: Long): List<RecordingBookmark>

    @Query("SELECT * FROM recording_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarksFlow(): Flow<List<RecordingBookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: RecordingBookmark): Long

    @Update
    suspend fun updateBookmark(bookmark: RecordingBookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: RecordingBookmark)

    @Query("DELETE FROM recording_bookmarks WHERE recordingId = :recordingId")
    suspend fun deleteBookmarksForRecording(recordingId: Long)
}

