package com.example.idnliverecorder.library

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.idnliverecorder.core.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository querying MediaStore for all video files in Movies/IDNLiveRecorder/ (Phase 6).
 */
class RecordingLibraryRepository(private val context: Context) {

    companion object {
        private const val TAG = "RecordingLibraryRepo"
    }

    enum class SortOption {
        NEWEST_FIRST,
        OLDEST_FIRST,
        MEMBER_NAME_ASC,
        SIZE_DESC
    }

    /**
     * Loads all recordings from MediaStore, applies integrity checks, and returns items.
     */
    suspend fun loadRecordings(
        sortOption: SortOption = SortOption.NEWEST_FIRST,
        searchQuery: String = "",
        dateFilterMs: Long? = null
    ): List<RecordingLibraryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<RecordingLibraryItem>()

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.IS_PENDING)
        }

        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${Constants.TARGET_DIRECTORY_NAME}%")

        val sortClause = when (sortOption) {
            SortOption.NEWEST_FIRST -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            SortOption.OLDEST_FIRST -> "${MediaStore.Video.Media.DATE_ADDED} ASC"
            SortOption.MEMBER_NAME_ASC -> "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
            SortOption.SIZE_DESC -> "${MediaStore.Video.Media.SIZE} DESC"
        }

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortClause
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.IS_PENDING)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val isPending = if (pendingCol != -1) cursor.getInt(pendingCol) == 1 else false

                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else "1080p (FHD)"
                    val memberName = RecordingLibraryItem.extractMemberName(name)

                    // Run quick integrity check
                    val integrity = RecordingIntegrityValidator.checkFileIntegrity(
                        context = context,
                        uri = contentUri,
                        sizeBytes = size,
                        isPending = isPending
                    )

                    list.add(
                        RecordingLibraryItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            sizeBytes = size,
                            durationMs = if (duration > 0) duration else integrity.durationMs,
                            dateAddedMs = dateAdded,
                            resolution = resolution,
                            memberName = memberName,
                            videoCodec = "H.264 / AVC",
                            audioCodec = "AAC-LC",
                            isPending = isPending,
                            isCorrupted = !integrity.isValid,
                            integrityMessage = if (!integrity.isValid) integrity.message else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore recordings: ${e.message}", e)
        }

        // Apply in-memory search and date filtering
        var filtered = list.toList()

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            filtered = filtered.filter { item ->
                item.memberName.lowercase().contains(q) || item.displayName.lowercase().contains(q)
            }
        }

        if (dateFilterMs != null && dateFilterMs > 0) {
            val oneDayMs = 24 * 60 * 60 * 1000L
            filtered = filtered.filter { item ->
                item.dateAddedMs in dateFilterMs until (dateFilterMs + oneDayMs)
            }
        }

        filtered
    }
}

