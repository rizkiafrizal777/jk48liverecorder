package com.example.idnliverecorder.recording.direct

import android.util.Log
import java.io.File

/**
 * Detects the actual media format of a downloaded HLS segment.
 *
 * Uses Content-Type from HTTP response first (authoritative), then falls back to
 * magic byte inspection of the first 12 bytes of the file (independent of URL extension).
 *
 * Supported detection:
 * - Fragmented MP4 / CMAF / ISO BMFF (ftyp, moof, moov)
 * - MPEG-TS (sync byte 0x47)
 * - Unknown / unsupported
 *
 * SAFETY: Does NOT load entire file into memory. Only reads first 12 bytes for signature check.
 */
object HlsSegmentDetector {

    private const val TAG = "IDN_HLS_DETECTOR"

    enum class SegmentFormat {
        MPEG_TS,          // Transport Stream: sync byte 0x47 at byte 0
        FRAGMENTED_MP4,   // ISO BMFF: ftyp, moof, mdat, or moov box detected
        UNKNOWN           // Unrecognized or empty file
    }

    data class DownloadedSegment(
        val file: File,
        val format: SegmentFormat,
        val sizeBytes: Long,
        val contentType: String? = null,
        val httpStatusCode: Int = 200,
        val isInitSegment: Boolean = false
    )

    /**
     * Detects format for a given segment file and optional Content-Type string.
     * Content-Type takes precedence. Falls back to magic byte inspection.
     */
    fun detectFormat(file: File, contentType: String?): SegmentFormat {
        // 1. Content-Type-based detection (authoritative)
        if (!contentType.isNullOrBlank()) {
            val ct = contentType.lowercase().substringBefore(";").trim()
            return when {
                ct == "video/mp4" ||
                ct == "video/iso.segment" ||
                ct == "video/mp4f" ||
                ct == "audio/mp4" ||
                ct.contains("mp4") -> {
                    Log.d(TAG, "Detected fMP4 by Content-Type: $ct")
                    SegmentFormat.FRAGMENTED_MP4
                }
                ct == "video/mp2t" ||
                ct == "video/mpeg" ||
                ct == "audio/mp2t" ||
                ct.contains("mp2t") ||
                ct.contains("mpeg-ts") ||
                ct.contains("mpeg2ts") -> {
                    Log.d(TAG, "Detected MPEG-TS by Content-Type: $ct")
                    SegmentFormat.MPEG_TS
                }
                else -> {
                    // Content-Type present but unrecognized — fall through to magic bytes
                    Log.d(TAG, "Unrecognized Content-Type '$ct', falling back to magic bytes")
                    detectByMagicBytes(file)
                }
            }
        }

        // 2. Magic byte inspection
        return detectByMagicBytes(file)
    }

    /**
     * Inspects the first 12 bytes of the file to detect ISO BMFF (fMP4) or MPEG-TS.
     *
     * MPEG-TS: First byte is always 0x47 (sync byte)
     * ISO BMFF (fMP4): Starts with a 4-byte big-endian size + 4-byte box type
     *   - Valid box types at start: ftyp, moof, moov, mdat, sidx, styp, emsg
     */
    fun detectByMagicBytes(file: File): SegmentFormat {
        if (!file.exists() || file.length() < 8) {
            Log.w(TAG, "File too small or missing for magic byte detection: ${file.name} (${file.length()} bytes)")
            return SegmentFormat.UNKNOWN
        }

        return try {
            val header = file.inputStream().use { it.readNBytes(16) }
            val hexString = header.joinToString(" ") { "%02X".format(it) }

            // MPEG-TS: sync byte 0x47 at position 0
            if (header.isNotEmpty() && header[0] == 0x47.toByte()) {
                Log.d(TAG, "Detected MPEG-TS by sync byte 0x47 for ${file.name} (first16Hex: [$hexString])")
                return SegmentFormat.MPEG_TS
            }

            // ISO BMFF: Check for valid 4-byte box type at bytes 4–7
            // Box types appearing at the start of a valid MP4-family file:
            val validMp4Boxes = setOf("ftyp", "moof", "moov", "mdat", "sidx", "styp", "emsg")
            if (header.size >= 8) {
                val boxType = String(header, 4, 4, Charsets.ISO_8859_1)
                if (boxType in validMp4Boxes) {
                    Log.d(TAG, "Detected fMP4/ISOBMFF by box type '$boxType' for ${file.name} (first16Hex: [$hexString])")
                    return SegmentFormat.FRAGMENTED_MP4
                }
            }

            Log.w(TAG, "Unknown format for segment: ${file.name} (first16Hex: [$hexString])")
            SegmentFormat.UNKNOWN

        } catch (e: Exception) {
            Log.e(TAG, "Magic byte detection failed for ${file.name}: ${e.message}", e)
            SegmentFormat.UNKNOWN
        }
    }

    /**
     * Verifies that an init segment file starts with a valid ISOBMFF box (ftyp or moov).
     */
    fun isValidInitSegment(file: File): Boolean {
        if (!file.exists() || file.length() < 8) {
            Log.w(TAG, "Init segment missing or empty: ${file.name} (length=${file.length()})")
            return false
        }
        return try {
            val header = file.inputStream().use { it.readNBytes(16) }
            if (header.size < 8) return false
            val boxType = String(header, 4, 4, Charsets.ISO_8859_1)
            val isValid = boxType in setOf("ftyp", "moov", "styp")
            val hexString = header.joinToString(" ") { "%02X".format(it) }
            Log.d(TAG, "Init segment check: file=${file.name}, boxType='$boxType', isValid=$isValid, first16Hex=[$hexString]")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Init segment validation error: ${e.message}", e)
            false
        }
    }
}

