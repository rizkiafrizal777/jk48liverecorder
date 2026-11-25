package com.example.idnliverecorder.recording.direct

/** Schedules recording finalization before Android's background data-sync FGS limit. */
internal class RecordingBackgroundTimeoutPolicy(
    private val platformLimitMs: Long = PLATFORM_LIMIT_MS,
    private val finalizationBufferMs: Long = FINALIZATION_BUFFER_MS
) {
    private var backgroundStartedAtMs: Long? = null

    init {
        require(platformLimitMs > 0L)
        require(finalizationBufferMs in 0 until platformLimitMs)
    }

    fun onAppBackgrounded(nowMs: Long) {
        if (backgroundStartedAtMs == null) backgroundStartedAtMs = nowMs
    }

    fun onAppForegrounded() {
        backgroundStartedAtMs = null
    }

    fun millisUntilGracefulStop(nowMs: Long): Long? {
        val backgroundStartedAt = backgroundStartedAtMs ?: return null
        val deadlineMs = backgroundStartedAt + platformLimitMs - finalizationBufferMs
        return (deadlineMs - nowMs).coerceAtLeast(0L)
    }

    companion object {
        const val PLATFORM_LIMIT_MS = 6 * 60 * 60 * 1000L
        const val FINALIZATION_BUFFER_MS = 15 * 60 * 1000L
    }
}
