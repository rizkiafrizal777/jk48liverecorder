package com.example.idnliverecorder.recording

data class RecordingConfig(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val frameRate: Int = 30,
    val bitRate: Int = 6_000_000,
    val mimeType: String = "video/avc",
    val isFallbackResolution: Boolean = false,
    // Audio config (Phase 3)
    val audioSampleRate: Int = 44100,
    val audioChannels: Int = 2,
    val audioBitrate: Int = 128_000,
    val includeAudio: Boolean = true
) {
    val resolutionLabel: String
        get() = "${width}x${height} @ ${frameRate}fps"

    val codecLabel: String
        get() = if (includeAudio) "H.264 / AAC" else "H.264 / Video Only"
}
