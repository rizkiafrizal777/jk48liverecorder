package com.example.idnliverecorder.core

import android.media.AudioFormat

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "idn_recording_channel"
    const val NOTIFICATION_ID = 1048

    const val ACTION_START_RECORDING = "com.example.idnliverecorder.action.START"
    const val ACTION_STOP_RECORDING = "com.example.idnliverecorder.action.STOP"

    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"

    const val TARGET_DIRECTORY_NAME = "IDNLiveRecorder"
    const val VIDEO_MIME_TYPE = "video/mp4"

    const val DEFAULT_FRAME_RATE = 30
    const val DEFAULT_VIDEO_BITRATE = 6_000_000 // 6 Mbps
    const val DEFAULT_I_FRAME_INTERVAL = 1 // seconds

    // Audio capture constants (Phase 3)
    const val AUDIO_MIME_TYPE = "audio/mp4a-latm" // AAC-LC
    const val DEFAULT_AUDIO_BITRATE = 128_000 // 128 kbps
    const val AUDIO_SAMPLE_RATE_48K = 48000
    const val AUDIO_SAMPLE_RATE_44K = 44100
    const val AUDIO_CHANNELS = AudioFormat.CHANNEL_IN_STEREO
    const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    const val AUDIO_AAC_PROFILE = 2 // MediaCodecInfo.CodecProfileLevel.AACObjectLC

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
