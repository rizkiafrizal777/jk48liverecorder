package com.example.idnliverecorder.recording.source

/**
 * Defines the type of live source used for recording.
 */
enum class LiveSourceType(val displayName: String) {
    /**
     * Standard device screen capture using MediaProjection.
     * Records the entire visible screen including status bar, UI overlays,
     * and any other applications opened during recording.
     */
    FullScreenRecording("Full Screen Recording"),

    /**
     * Future direct live stream ingest via official IDN SDK / API.
     * Currently unavailable until an authorized integration is provided.
     */
    OfficialLiveStream("Official Live Stream"),

    /**
     * Unsupported or invalid source mode.
     */
    Unsupported("Unsupported Source")
}

