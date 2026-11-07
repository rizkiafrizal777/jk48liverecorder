package com.example.idnliverecorder.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log

object RecordingFileValidator {
    private const val TAG = "RecordingFileValidator"

    data class ValidationResult(
        val isValid: Boolean,
        val sizeBytes: Long = 0L,
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val errorMessage: String? = null
    )

    fun validateRecordedFile(context: Context, uri: Uri): ValidationResult {
        var size: Long = 0L
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                size = pfd.statSize
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file descriptor for validation: ${e.message}", e)
            return ValidationResult(
                isValid = false,
                errorMessage = "Cannot access recorded file: ${e.message}"
            )
        }

        if (size <= 0) {
            Log.e(TAG, "Recorded file size is 0 bytes.")
            return ValidationResult(
                isValid = false,
                sizeBytes = 0L,
                errorMessage = "Recording file is empty (0 bytes)."
            )
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0

            if (hasVideo == null && durationMs <= 0) {
                ValidationResult(
                    isValid = false,
                    sizeBytes = size,
                    errorMessage = "MP4 file header is corrupt or missing video tracks."
                )
            } else {
                ValidationResult(
                    isValid = true,
                    sizeBytes = size,
                    durationMs = durationMs,
                    width = width,
                    height = height
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever failed to parse MP4: ${e.message}", e)
            ValidationResult(
                isValid = false,
                sizeBytes = size,
                errorMessage = "Recorded file header is invalid: ${e.message}"
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}

