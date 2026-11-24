package com.example.idnliverecorder.library

import android.os.Environment
import android.os.StatFs
import android.util.Log

/**
 * Analyzes storage utilization for the IDNLiveRecorder recordings folder (Phase 6).
 */
object RecordingStorageAnalyzer {

    private const val TAG = "StorageAnalyzer"

    data class StorageUsageInfo(
        val totalRecordingsBytes: Long,
        val recordingsCount: Int,
        val availableDeviceBytes: Long,
        val totalDeviceBytes: Long,
        val folderUsagePercentage: Float
    ) {
        val freeStoragePercentage: Float
            get() = if (totalDeviceBytes > 0) (availableDeviceBytes.toFloat() / totalDeviceBytes) * 100f else 0f
    }

    /**
     * Calculates storage breakdown based on the loaded recordings list.
     */
    fun analyze(recordings: List<RecordingLibraryItem>): StorageUsageInfo {
        val totalRecBytes = recordings.sumOf { it.sizeBytes }
        val count = recordings.size

        var availBytes = 0L
        var totalBytes = 0L

        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            availBytes = stat.availableBytes
            totalBytes = stat.totalBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating device storage stats: ${e.message}")
        }

        val percentage = if (totalBytes > 0) {
            (totalRecBytes.toFloat() / totalBytes) * 100f
        } else {
            0f
        }

        return StorageUsageInfo(
            totalRecordingsBytes = totalRecBytes,
            recordingsCount = count,
            availableDeviceBytes = availBytes,
            totalDeviceBytes = totalBytes,
            folderUsagePercentage = percentage
        )
    }
}

