package com.example.idnliverecorder.library

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Controller for file management operations on recorded videos (Phase 6).
 *
 * Implements:
 * - Rename via MediaStore with RecoverableSecurityException handling
 * - Scoped deletion via MediaStore ContentResolver
 * - Secure Android Sharesheet sharing via Content URI and FLAG_GRANT_READ_URI_PERMISSION
 * - Export via Storage Access Framework (SAF)
 *
 * SAFETY RULES:
 * - Never deletes or alters files outside the target directory.
 * - Never exposes raw file paths in share intents.
 */
class RecordingManagementController(private val context: Context) {

    companion object {
        private const val TAG = "RecordingManagement"
    }

    sealed interface RenameResult {
        object Success : RenameResult
        data class PermissionRequired(val securityException: RecoverableSecurityException) : RenameResult
        data class Failure(val errorMessage: String) : RenameResult
    }

    /**
     * Renames a video file in MediaStore.
     */
    suspend fun renameRecording(item: RecordingLibraryItem, newBaseName: String): RenameResult = withContext(Dispatchers.IO) {
        val sanitized = newBaseName.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val finalName = if (sanitized.endsWith(".mp4", ignoreCase = true)) sanitized else "$sanitized.mp4"

        if (finalName.isBlank() || finalName == ".mp4") {
            return@withContext RenameResult.Failure("Nama file tidak boleh kosong.")
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
        }

        try {
            val rows = context.contentResolver.update(item.uri, values, null, null)
            if (rows > 0) {
                Log.d(TAG, "Renamed ${item.displayName} -> $finalName successfully.")
                RenameResult.Success
            } else {
                RenameResult.Failure("Gagal memperbarui nama file pada MediaStore.")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                Log.w(TAG, "RecoverableSecurityException encountered for rename.")
                RenameResult.PermissionRequired(e)
            } else {
                Log.e(TAG, "SecurityException renaming file: ${e.message}")
                RenameResult.Failure("Izin akses ditolak untuk mengubah nama file.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file: ${e.message}", e)
            RenameResult.Failure(e.localizedMessage ?: "Terjadi kesalahan saat mengubah nama.")
        }
    }

    /**
     * Deletes a video recording from MediaStore.
     */
    suspend fun deleteRecording(item: RecordingLibraryItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(item.uri, null, null)
            if (rows > 0) {
                Log.d(TAG, "Deleted ${item.displayName} ($rows row deleted).")
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("File tidak ditemukan atau gagal dihapus dari MediaStore."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file ${item.uri}: ${e.message}", e)
            Result.failure(e)
        }
    }

    data class ShareIntentSpec(
        val action: String,
        val type: String,
        val streamUri: Uri,
        val subject: String,
        val flags: Int
    )

    /**
     * Builds the ShareIntent specification model for verification and Intent construction.
     */
    fun createShareIntentSpec(item: RecordingLibraryItem): ShareIntentSpec {
        return ShareIntentSpec(
            action = Intent.ACTION_SEND,
            type = "video/mp4",
            streamUri = item.uri,
            subject = item.displayName,
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    /**
     * Creates an Android Sharesheet Intent sharing the video via Content URI.
     */
    fun createShareIntent(item: RecordingLibraryItem): Intent {
        val spec = createShareIntentSpec(item)
        val shareIntent = Intent(spec.action).apply {
            type = spec.type
            putExtra(Intent.EXTRA_STREAM, spec.streamUri)
            putExtra(Intent.EXTRA_SUBJECT, spec.subject)
            addFlags(spec.flags)
        }
        return Intent.createChooser(shareIntent, "Bagikan Rekaman Live JKT48")
    }

    data class MultiShareIntentSpec(
        val action: String,
        val type: String,
        val streamUris: ArrayList<Uri>,
        val flags: Int
    )

    fun createMultiShareIntentSpec(items: List<RecordingLibraryItem>): MultiShareIntentSpec {
        val uris = ArrayList<Uri>(items.map { it.uri })
        return MultiShareIntentSpec(
            action = Intent.ACTION_SEND_MULTIPLE,
            type = "video/mp4",
            streamUris = uris,
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    /**
     * Creates an Android Sharesheet Intent sharing multiple videos simultaneously.
     */
    fun createMultiShareIntent(items: List<RecordingLibraryItem>): Intent {
        val spec = createMultiShareIntentSpec(items)
        val shareIntent = Intent(spec.action).apply {
            type = spec.type
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, spec.streamUris)
            addFlags(spec.flags)
        }
        return Intent.createChooser(shareIntent, "Bagikan ${items.size} Rekaman Live JKT48")
    }

    /**
     * Deletes multiple recordings with count returned.
     */
    suspend fun deleteMultipleRecordings(items: List<RecordingLibraryItem>): Result<Int> = withContext(Dispatchers.IO) {
        var count = 0
        try {
            for (item in items) {
                val rows = context.contentResolver.delete(item.uri, null, null)
                if (rows > 0) count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Error in multi-delete: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Exports a recording file to a user-selected destination URI via Storage Access Framework.
     */
    suspend fun exportRecordingToUri(sourceUri: Uri, destinationUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IllegalStateException("Gagal membaca file sumber."))

            val outputStream = resolver.openOutputStream(destinationUri)
                ?: run {
                    inputStream.close()
                    return@withContext Result.failure(IllegalStateException("Gagal membuka lokasi tujuan ekspor."))
                }

            val bytesCopied = inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Exported $bytesCopied bytes from $sourceUri to $destinationUri")
            Result.success(bytesCopied)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting video: ${e.message}", e)
            Result.failure(e)
        }
    }
}

