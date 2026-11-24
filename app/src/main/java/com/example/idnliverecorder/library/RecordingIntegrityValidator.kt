package com.example.idnliverecorder.library

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * Validates recording file integrity and handles file recovery (Phase 6).
 *
 * Checks:
 * - Empty / invalid file sizes
 * - Incomplete or unfinalized files (IS_PENDING)
 * - MediaExtractor / MediaMetadataRetriever track parsing
 *
 * SAFETY & POLICY:
 * - Never auto-deletes corrupted files without explicit confirmation.
 * - Never claims recovery succeeded unless validated by parser.
 */
object RecordingIntegrityValidator {

    private const val TAG = "RecordingIntegrity"

    enum class IntegrityStatus {
        VALID,
        EMPTY_FILE,
        PENDING_UNFINALIZED,
        UNREADABLE_METADATA,
        CORRUPTED
    }

    data class IntegrityReport(
        val status: IntegrityStatus,
        val isRecoverable: Boolean,
        val message: String,
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val hasVideoTrack: Boolean = false,
        val hasAudioTrack: Boolean = false
    ) {
        val isValid: Boolean get() = status == IntegrityStatus.VALID
    }

    /**
     * Inspects a video file via ContentResolver, MediaExtractor, and MediaMetadataRetriever.
     */
    fun checkFileIntegrity(
        context: Context,
        uri: Uri,
        sizeBytes: Long,
        isPending: Boolean
    ): IntegrityReport {
        if (sizeBytes <= 0) {
            return IntegrityReport(
                status = IntegrityStatus.EMPTY_FILE,
                isRecoverable = false,
                message = "File rekaman kosong (0 bytes)."
            )
        }

        if (isPending) {
            // Check if underlying media is actually intact despite pending flag
            val probe = probeMediaTracks(context, uri)
            return if (probe.hasVideoTrack && probe.durationMs > 0) {
                IntegrityReport(
                    status = IntegrityStatus.PENDING_UNFINALIZED,
                    isRecoverable = true,
                    message = "File belum difinalisasi oleh sistem, namun data media valid dan dapat dipulihkan.",
                    durationMs = probe.durationMs,
                    width = probe.width,
                    height = probe.height,
                    hasVideoTrack = probe.hasVideoTrack,
                    hasAudioTrack = probe.hasAudioTrack
                )
            } else {
                IntegrityReport(
                    status = IntegrityStatus.CORRUPTED,
                    isRecoverable = false,
                    message = "File pending tidak selesai dan tidak memiliki header video yang valid."
                )
            }
        }

        val probe = probeMediaTracks(context, uri)
        if (!probe.hasVideoTrack) {
            return IntegrityReport(
                status = IntegrityStatus.UNREADABLE_METADATA,
                isRecoverable = false,
                message = "Track video tidak dapat dibaca atau rusak."
            )
        }

        return IntegrityReport(
            status = IntegrityStatus.VALID,
            isRecoverable = false,
            message = "File video valid dan siap diputar.",
            durationMs = probe.durationMs,
            width = probe.width,
            height = probe.height,
            hasVideoTrack = probe.hasVideoTrack,
            hasAudioTrack = probe.hasAudioTrack
        )
    }

    private data class TrackProbeResult(
        val hasVideoTrack: Boolean,
        val hasAudioTrack: Boolean,
        val durationMs: Long,
        val width: Int,
        val height: Int
    )

    private fun probeMediaTracks(context: Context, uri: Uri): TrackProbeResult {
        var hasVideo = false
        var hasAudio = false
        var durationMs = 0L
        var width = 0
        var height = 0

        // 1. MediaMetadataRetriever probe
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            width = widthStr?.toIntOrNull() ?: 0

            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            height = heightStr?.toIntOrNull() ?: 0

            val hasVideoStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            hasVideo = hasVideoStr == "yes" || (width > 0 && height > 0)

            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            hasAudio = hasAudioStr == "yes"

            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever probe failed for $uri: ${e.message}")
        }

        // 2. MediaExtractor fallback check if retriever had incomplete data
        if (!hasVideo) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        hasVideo = true
                        if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                            width = format.getInteger(MediaFormat.KEY_WIDTH)
                        }
                        if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            height = format.getInteger(MediaFormat.KEY_HEIGHT)
                        }
                    } else if (mime.startsWith("audio/")) {
                        hasAudio = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaExtractor probe failed for $uri: ${e.message}")
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }
        }

        return TrackProbeResult(
            hasVideoTrack = hasVideo,
            hasAudioTrack = hasAudio,
            durationMs = durationMs,
            width = width,
            height = height
        )
    }

    /**
     * Attempts recovery of an unfinalized / pending video.
     * Verifies media validity first, then clears IS_PENDING flag in MediaStore.
     */
    fun attemptRecovery(context: Context, uri: Uri): Result<Boolean> {
        Log.d(TAG, "Attempting recovery for video: $uri")
        val probe = probeMediaTracks(context, uri)

        if (!probe.hasVideoTrack || probe.durationMs <= 0) {
            val err = "Tidak dapat memulihkan file: Header video tidak lengkap atau tidak memiliki durasi valid."
            Log.e(TAG, err)
            return Result.failure(IllegalStateException(err))
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
            put(MediaStore.Video.Media.DURATION, probe.durationMs)
            if (probe.width > 0 && probe.height > 0) {
                put(MediaStore.Video.Media.WIDTH, probe.width)
                put(MediaStore.Video.Media.HEIGHT, probe.height)
            }
        }

        return try {
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                Log.d(TAG, "Recovery successful for $uri (rows updated: $rows, duration: ${probe.durationMs}ms)")
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("ContentResolver gagal memperbarui status file."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recovery exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}

