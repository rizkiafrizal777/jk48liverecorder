package com.example.idnliverecorder.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.idnliverecorder.core.Constants
import com.example.idnliverecorder.data.DetectionStatus

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionRx"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action == Constants.ACTION_IGNORE_DETECTION) {
            val detectionId = intent.getStringExtra(Constants.EXTRA_DETECTION_ID)
            Log.d(TAG, "User chose 'Abaikan' for detection: $detectionId")

            val detectionManager = LiveDetectionManager.getInstance(context)
            detectionManager.dismissDetectionNotification()

            if (!detectionId.isNullOrBlank()) {
                val repository = LiveDetectionRepository.getInstance(context)
                repository.updateDetectionStatus(detectionId, DetectionStatus.Ignored)
            }
        }
    }
}

