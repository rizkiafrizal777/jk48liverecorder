package com.example.idnliverecorder.recording.direct

/**
 * Status hierarchy for Phase 5 Direct Stream Recording.
 *
 * AUTHORIZATION_REQUIRED represents a stream server response that denies access (HTTP 401/403).
 */
enum class DirectStreamStatus(val label: String) {
    LIVE("LIVE"),
    SOURCE_AVAILABLE("SOURCE AVAILABLE"),
    SOURCE_UNAVAILABLE("SOURCE UNAVAILABLE"),
    AUTHORIZATION_REQUIRED("AUTHORIZATION REQUIRED"),
    READY_TO_RECORD("READY TO RECORD"),
    RECORDING("RECORDING"),
    FINALIZING("FINALIZING"),
    COMPLETED("COMPLETED"),
    ERROR("ERROR")
}

