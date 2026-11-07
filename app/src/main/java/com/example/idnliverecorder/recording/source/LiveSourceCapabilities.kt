package com.example.idnliverecorder.recording.source

import android.os.Build

/**
 * Declares the capabilities, limitations, and device compatibility for a [LiveSourceProvider].
 */
data class LiveSourceCapabilities(
    val type: LiveSourceType,
    val isAvailableOnDevice: Boolean,
    val minSdk: Int,
    val requiresMediaProjection: Boolean,
    val supportsBackgroundRecording: Boolean,
    val recordsOtherApplications: Boolean,
    val recordsUiElements: Boolean,
    val displayName: String,
    val description: String,
    val availabilityNotice: String
) {
    companion object {
        fun forFullScreen(currentSdk: Int = Build.VERSION.SDK_INT): LiveSourceCapabilities {
            return LiveSourceCapabilities(
                type = LiveSourceType.FullScreenRecording,
                isAvailableOnDevice = currentSdk >= Build.VERSION_CODES.Q,
                minSdk = Build.VERSION_CODES.Q,
                requiresMediaProjection = true,
                supportsBackgroundRecording = false,
                recordsOtherApplications = true,
                recordsUiElements = true,
                displayName = "Full Screen Recording",
                description = "Records the visible device screen. Note: Any other open applications, notifications, or chat overlays will also be recorded.",
                availabilityNotice = "Available on this device (Android 10+ / API 29+)."
            )
        }

        fun forOfficialStream(): LiveSourceCapabilities {
            return LiveSourceCapabilities(
                type = LiveSourceType.OfficialLiveStream,
                isAvailableOnDevice = false, // Never claimed available without authorized integration
                minSdk = Build.VERSION_CODES.Q,
                requiresMediaProjection = false,
                supportsBackgroundRecording = true,
                recordsOtherApplications = false,
                recordsUiElements = false,
                displayName = "Official Live Stream Recording",
                description = "Direct stream capture via official IDN SDK / partner API without recording the device screen.",
                availabilityNotice = "Official live stream integration is not available."
            )
        }

        fun forUnsupported(): LiveSourceCapabilities {
            return LiveSourceCapabilities(
                type = LiveSourceType.Unsupported,
                isAvailableOnDevice = false,
                minSdk = Int.MAX_VALUE,
                requiresMediaProjection = false,
                supportsBackgroundRecording = false,
                recordsOtherApplications = false,
                recordsUiElements = false,
                displayName = "Unsupported Source",
                description = "This recording source is not supported on this configuration.",
                availabilityNotice = "Unsupported."
            )
        }
    }
}

