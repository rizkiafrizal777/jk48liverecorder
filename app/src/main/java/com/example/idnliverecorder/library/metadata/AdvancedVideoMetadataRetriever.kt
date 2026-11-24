package com.example.idnliverecorder.library.metadata

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.idnliverecorder.core.formatFileSize
import com.example.idnliverecorder.database.IDNRecorderDatabase
import com.example.idnliverecorder.database.RecordingMetadataCache
import com.example.idnliverecorder.library.RecordingLibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detailed technical metadata information for a video recording (Phase 7).
 */
data class AdvancedRecordingMetadata(
    val fileName: String,
    val memberName: String,
    val recordingDateFormatted: String,
    val durationFormatted: String,
    val durationMs: Long,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val resolution: String,
    val videoCodec: String,
    val audioCodec: String,
    val frameRate: String,
    val bitrateFormatted: String,
    val bitrateBps: Long?,
    val mimeType: String,
    val contentUri: String,
    val playbackCompatibility: String
)

object AdvancedVideoMetadataRetriever {

    private const val TAG = "AdvMetadataRetriever"

    /**
     * Extracts full technical metadata for a recording item, using cached values if present.
     */
    suspend fun getMetadata(context: Context, item: RecordingLibraryItem): AdvancedRecordingMetadata = withContext(Dispatchers.IO) {
        val db = IDNRecorderDatabase.getInstance(context)
        val cacheDao = db.metadataCacheDao()
        val cached = cacheDao.getMetadata(item.id)

        var frameRateVal: Float? = cached?.frameRate
        var bitrateVal: Long? = cached?.bitrate
        var mimeTypeVal: String? = cached?.mimeType
        var extractedVideoCodec: String? = null
        var extractedAudioCodec: String? = null

        if (cached == null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, item.uri)

                val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                bitrateVal = bitrateStr?.toLongOrNull()

                val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                mimeTypeVal = mimeStr

                val extractor = MediaExtractor()
                extractor.setDataSource(context, item.uri, null)
                val trackCount = extractor.trackCount
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            frameRateVal = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                        extractedVideoCodec = formatCodecName(mime)
                    } else if (mime.startsWith("audio/")) {
                        extractedAudioCodec = formatCodecName(mime)
                    }
                }
                extractor.release()
                retriever.release()

                // Cache the probe results
                cacheDao.insertOrUpdate(
                    RecordingMetadataCache(
                        recordingId = item.id,
                        recordingUri = item.uri.toString(),
                        frameRate = frameRateVal,
                        bitrate = bitrateVal,
                        mimeType = mimeTypeVal
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed probing advanced metadata for ${item.displayName}: ${e.message}")
            }
        }

        val formattedDate = formatEpochToDate(item.dateAddedMs)
        val formattedDuration = formatDuration(item.durationMs)
        val formattedSize = item.sizeBytes.formatFileSize()

        val frameRateDisplay = if (frameRateVal != null && frameRateVal > 0) {
            String.format("%.1f fps", frameRateVal)
        } else {
            "Unknown"
        }

        val bitrateDisplay = if (bitrateVal != null && bitrateVal > 0) {
            val kbps = bitrateVal / 1000
            if (kbps >= 1000) String.format("%.2f Mbps", kbps / 1000f) else "$kbps kbps"
        } else {
            "Unknown"
        }

        val mimeDisplay = mimeTypeVal?.ifBlank { null } ?: "video/mp4"
        val vCodec = extractedVideoCodec ?: item.videoCodec.ifBlank { "H.264 / AVC" }
        val aCodec = extractedAudioCodec ?: item.audioCodec.ifBlank { "AAC-LC" }

        val compatibility = if (item.isPlayable) {
            "Optimal — Akselerasi Hardware (H.264 / AAC)"
        } else if (item.isPending) {
            "Tertunda (File belum difinalisasi)"
        } else {
            "Tidak Kompatibel (File Rusak/0-byte)"
        }

        AdvancedRecordingMetadata(
            fileName = item.displayName,
            memberName = item.memberName,
            recordingDateFormatted = formattedDate,
            durationFormatted = formattedDuration,
            durationMs = item.durationMs,
            fileSizeFormatted = formattedSize,
            fileSizeBytes = item.sizeBytes,
            resolution = item.resolution.ifBlank { "Unknown" },
            videoCodec = vCodec,
            audioCodec = aCodec,
            frameRate = frameRateDisplay,
            bitrateFormatted = bitrateDisplay,
            bitrateBps = bitrateVal,
            mimeType = mimeDisplay,
            contentUri = item.uri.toString(),
            playbackCompatibility = compatibility
        )
    }

    private fun formatCodecName(mime: String): String {
        return when (mime.lowercase()) {
            "video/avc" -> "H.264 / AVC"
            "video/hevc" -> "H.265 / HEVC"
            "video/mp4v-es" -> "MPEG-4 Video"
            "video/x-vnd.on2.vp8" -> "VP8"
            "video/x-vnd.on2.vp9" -> "VP9"
            "video/av01" -> "AV1"
            "audio/mp4a-latm" -> "AAC-LC"
            "audio/mpeg" -> "MP3"
            "audio/opus" -> "Opus"
            else -> mime
        }
    }

    private fun formatEpochToDate(ms: Long): String {
        return if (ms <= 0) "Unknown" else {
            val sdf = java.text.SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", java.util.Locale("id", "ID"))
            sdf.format(java.util.Date(ms))
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "Unknown"
        val totalSeconds = durationMs / 1000
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

