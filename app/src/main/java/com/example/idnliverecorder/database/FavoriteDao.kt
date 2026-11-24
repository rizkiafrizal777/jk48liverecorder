package com.example.idnliverecorder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM recording_favorites WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getAllFavoritesFlow(): Flow<List<RecordingFavorite>>

    @Query("SELECT * FROM recording_favorites WHERE isFavorite = 1")
    suspend fun getAllFavorites(): List<RecordingFavorite>

    @Query("SELECT isFavorite FROM recording_favorites WHERE recordingId = :recordingId LIMIT 1")
    suspend fun isFavorite(recordingId: Long): Boolean?

    @Query("SELECT isFavorite FROM recording_favorites WHERE recordingId = :recordingId LIMIT 1")
    fun isFavoriteFlow(recordingId: Long): Flow<Boolean?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFavorite(favorite: RecordingFavorite)

    @Query("DELETE FROM recording_favorites WHERE recordingId = :recordingId")
    suspend fun removeFavorite(recordingId: Long)
}

