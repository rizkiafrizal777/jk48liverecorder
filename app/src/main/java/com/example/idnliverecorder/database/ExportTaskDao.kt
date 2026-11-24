package com.example.idnliverecorder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportTaskDao {

    @Query("SELECT * FROM export_tasks ORDER BY startedAt DESC")
    fun getAllTasksFlow(): Flow<List<ExportTask>>

    @Query("SELECT * FROM export_tasks WHERE status IN ('QUEUED', 'RUNNING') ORDER BY startedAt ASC")
    fun getActiveTasksFlow(): Flow<List<ExportTask>>

    @Query("SELECT * FROM export_tasks ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentExportsFlow(limit: Int = 10): Flow<List<ExportTask>>

    @Query("SELECT * FROM export_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): ExportTask?

    @Query("SELECT * FROM export_tasks WHERE sourceUri = :sourceUri AND status IN ('QUEUED', 'RUNNING') LIMIT 1")
    suspend fun getActiveTaskBySourceUri(sourceUri: String): ExportTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ExportTask): Long

    @Update
    suspend fun updateTask(task: ExportTask)

    @Query("UPDATE export_tasks SET status = :status, errorMessage = :error, completedAt = :completedAt WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: String, error: String?, completedAt: Long?)

    @Query("UPDATE export_tasks SET copiedBytes = :copiedBytes, progressPercent = :percent WHERE id = :id")
    suspend fun updateProgress(id: Long, copiedBytes: Long, percent: Int)
}

