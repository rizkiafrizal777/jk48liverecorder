package com.example.idnliverecorder.core

object Constants {
    const val TARGET_DIRECTORY_NAME = "IDNLiveRecorder"
    const val VIDEO_MIME_TYPE = "video/mp4"

    // Live Detection Constants
    const val NOTIFICATION_CHANNEL_ID_DETECTION = "idn_live_detection_channel"
    const val NOTIFICATION_ID_LIVE_DETECTION = 2048

    const val ACTION_VIEW_DETECTION = "com.example.idnliverecorder.action.VIEW_DETECTION"
    const val ACTION_IGNORE_DETECTION = "com.example.idnliverecorder.action.IGNORE_DETECTION"

    const val EXTRA_DETECTION_ID = "extra_detection_id"
    const val EXTRA_STREAMER_NAME = "extra_streamer_name"

    val DEFAULT_LIVE_KEYWORDS = listOf("live", "sedang live", "started a live", "is live")
    const val DEFAULT_COOLDOWN_MINUTES = 5
}
