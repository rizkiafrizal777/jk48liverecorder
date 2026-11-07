package com.example.idnliverecorder.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.idnliverecorder.core.Constants

class RecordingManager(private val context: Context) {

    fun startRecording(resultCode: Int, resultData: Intent, streamerName: String? = null) {
        val serviceIntent = Intent(context, ScreenRecordingService::class.java).apply {
            action = Constants.ACTION_START_RECORDING
            putExtra(Constants.EXTRA_RESULT_CODE, resultCode)
            putExtra(Constants.EXTRA_RESULT_DATA, resultData)
            if (!streamerName.isNullOrBlank()) {
                putExtra(Constants.EXTRA_STREAMER_NAME, streamerName)
            }
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopRecording() {
        val serviceIntent = Intent(context, ScreenRecordingService::class.java).apply {
            action = Constants.ACTION_STOP_RECORDING
        }
        context.startService(serviceIntent)
    }

    val state = ScreenRecordingService.recordingState
}

