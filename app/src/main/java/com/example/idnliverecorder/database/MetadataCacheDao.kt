package com.example.idnliverecorder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MetadataCacheDao {

    @Query("SELECT * FROM recording_metadata_cache WHERE recordingId = :recordingId LIMIT 1")
    suspend fun getMetadata(recordingId: Long): RecordingMetadataCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: RecordingMetadataCache)

    @Query("DELETE FROM recording_metadata_cache WHERE recordingId = :recordingId")
    suspend fun deleteMetadata(recordingId: Long)
}

