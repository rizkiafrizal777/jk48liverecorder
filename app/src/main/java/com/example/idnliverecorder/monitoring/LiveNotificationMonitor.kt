package com.example.idnliverecorder.monitoring

import android.content.Context

class LiveNotificationMonitor(private val context: Context) {

    private val detectionManager = LiveDetectionManager.getInstance(context)
    private val detectionSettings = LiveDetectionSettings.getInstance(context)

    fun startMonitoring() {
        detectionSettings.setEnabled(true)
    }

    fun stopMonitoring() {
        detectionSettings.setEnabled(false)
    }

    fun isMonitoring(): Boolean = detectionManager.isDetectionActive()
}
