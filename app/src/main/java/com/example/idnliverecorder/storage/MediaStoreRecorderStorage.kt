package com.example.idnliverecorder.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.idnliverecorder.core.Constants
import com.example.idnliverecorder.data.RecordedVideoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreRecorderStorage {
    private const val TAG = "MediaStoreStorage"

    data class OutputSession(
        val uri: Uri,
        val displayName: String,
        val parcelFileDescriptor: ParcelFileDescriptor
    )

    fun createPendingVideo(context: Context): OutputSession {
        val resolver = context.contentResolver
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayName = "IDN_REC_${timeStamp}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, Constants.VIDEO_MIME_TYPE)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/${Constants.TARGET_DIRECTORY_NAME}"
            )
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to insert new video record into MediaStore.")

        val pfd = resolver.openFileDescriptor(uri, "rw")
            ?: run {
                resolver.delete(uri, null, null)
                throw IllegalStateException("Failed to open file descriptor for Uri: $uri")
            }

        Log.d(TAG, "Created pending video session: uri=$uri, displayName=$displayName")
        return OutputSession(uri = uri, displayName = displayName, parcelFileDescriptor = pfd)
    }

    fun finalizeVideo(
        context: Context,
        uri: Uri,
        displayName: String
    ): Result<RecordingFileValidator.ValidationResult> {
        val resolver = context.contentResolver
        val validation = RecordingFileValidator.validateRecordedFile(context, uri)

        if (!validation.isValid) {
            Log.w(TAG, "Validation failed for $uri: ${validation.errorMessage}. Cleaning up partial file.")
            try {
                resolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete corrupted recording: ${e.message}", e)
            }
            return Result.failure(
                IllegalStateException(validation.errorMessage ?: "Recorded video validation failed.")
            )
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
            if (validation.durationMs > 0) {
                put(MediaStore.Video.Media.DURATION, validation.durationMs)
            }
        }

        try {
            val rows = resolver.update(uri, values, null, null)
            Log.d(TAG, "Finalized video successfully: $displayName, rows updated: $rows, size: ${validation.sizeBytes} bytes")
            return Result.success(validation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update MediaStore pending status: ${e.message}", e)
            return Result.failure(e)
        }
    }

    fun discardVideo(context: Context, uri: Uri) {
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            Log.d(TAG, "Discarded video $uri (rows affected: $rows)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discard video: ${e.message}", e)
        }
    }

    fun fetchRecentRecordings(context: Context): List<RecordedVideoItem> {
        val list = mutableListOf<RecordedVideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${Constants.TARGET_DIRECTORY_NAME}%")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)

                    val resString = if (width > 0 && height > 0) "${width}x${height}" else "1080p"

                    list.add(
                        RecordedVideoItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            size = size,
                            durationMs = duration,
                            dateAddedMs = dateAdded,
                            resolution = resString
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying recent recordings: ${e.message}", e)
        }

        return list
    }
}

