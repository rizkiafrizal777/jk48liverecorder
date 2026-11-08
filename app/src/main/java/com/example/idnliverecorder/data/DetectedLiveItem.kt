package com.example.idnliverecorder.data

enum class DetectionStatus {
    Waiting,    // Awaiting user confirmation ("Lihat" -> "Mulai Rekam" or "Batal")
    Recording,  // User confirmed and recording started
    Ignored,    // User dismissed or tapped "Abaikan"
    Expired     // Expired due to cooldown or new stream detected
}

data class DetectedLiveItem(
    val id: String,
    val streamerName: String,
    val notificationContent: String,
    val detectedAtMs: Long,
    val status: DetectionStatus = DetectionStatus.Waiting,
    val packageName: String,
    val matchedKeyword: String
) {
    /**
     * Determines whether this item was created as a test simulation / demo.
     */
    val isSimulation: Boolean
        get() = id.startsWith("test_") ||
                packageName.contains("test", ignoreCase = true) ||
                matchedKeyword.contains("test", ignoreCase = true) ||
                matchedKeyword.contains("simulasi", ignoreCase = true) ||
                streamerName.contains("simulasi", ignoreCase = true)
}

