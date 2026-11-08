package com.example.idnliverecorder.recording.direct

/**
 * Status hierarchy for Phase 5 Direct Stream Recording.
 *
 * Enforces strict compliance:
 * - READY_TO_RECORD must NEVER be reached unless authorization is explicitly verified.
 * - Unauthorized sources remain locked at AUTHORIZATION_REQUIRED or SOURCE_UNAVAILABLE.
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
