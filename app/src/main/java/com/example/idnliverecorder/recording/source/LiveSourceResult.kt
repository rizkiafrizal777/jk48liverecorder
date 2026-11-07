package com.example.idnliverecorder.recording.source

/**
 * Result representing the preparation or initialization state of a [LiveSourceProvider].
 */
sealed interface LiveSourceResult {
    /**
     * Source is prepared and ready for recording.
     */
    data class Ready(val capabilities: LiveSourceCapabilities) : LiveSourceResult

    /**
     * Official source is not available.
     * Code: UNSUPPORTED_OFFICIAL_SOURCE
     */
    data class UnsupportedOfficialSource(
        val code: String = "UNSUPPORTED_OFFICIAL_SOURCE",
        val message: String = "Official live stream integration is not available."
    ) : LiveSourceResult

    /**
     * Missing system or runtime permissions (e.g. MediaProjection consent or RECORD_AUDIO).
     */
    data class MissingPermission(val message: String) : LiveSourceResult

    /**
     * Device OS version or hardware does not meet requirements.
     */
    data class DeviceNotSupported(val message: String) : LiveSourceResult

    /**
     * An unexpected initialization error occurred.
     */
    data class InitializationError(val message: String, val cause: Throwable? = null) : LiveSourceResult
}

