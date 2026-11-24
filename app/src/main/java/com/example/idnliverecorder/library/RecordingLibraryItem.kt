package com.example.idnliverecorder.library

import android.net.Uri

/**
 * Domain model representing a recorded livestream video in the Recording Library.
 */
data class RecordingLibraryItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateAddedMs: Long,
    val resolution: String,
    val memberName: String,
    val videoCodec: String = "H.264 / AVC",
    val audioCodec: String = "AAC-LC",
    val isPending: Boolean = false,
    val isCorrupted: Boolean = false,
    val integrityMessage: String? = null
) {
    val isPlayable: Boolean
        get() = !isCorrupted && !isPending && sizeBytes > 0

    companion object {
        /**
         * Extracts the streamer / member name from a standard recording filename:
         * e.g., "JKT48_Freya_Jayawardana_20261108_120000.mp4" -> "Freya Jayawardana"
         * or "IDN_REC_20261108_120000.mp4" -> "IDN Screen Recording"
         */
        fun extractMemberName(displayName: String): String {
            if (displayName.startsWith("JKT48_")) {
                val withoutPrefix = displayName.removePrefix("JKT48_")
                val parts = withoutPrefix.split("_")
                val cleanParts = parts.toMutableList()
                cleanParts[cleanParts.lastIndex] = cleanParts.last().substringBeforeLast(".")
                if (cleanParts.size >= 3 && cleanParts[cleanParts.size - 1].all { it.isDigit() } && cleanParts[cleanParts.size - 2].all { it.isDigit() }) {
                    return cleanParts.dropLast(2).joinToString(" ")
                } else if (cleanParts.size >= 2 && cleanParts.last().all { it.isDigit() }) {
                    return cleanParts.dropLast(1).joinToString(" ")
                } else {
                    return cleanParts.joinToString(" ")
                }
            } else if (displayName.startsWith("IDN_REC_")) {
                return "IDN Screen Recording"
            }
            return displayName.substringBeforeLast(".").replace("_", " ")
        }
    }
}

