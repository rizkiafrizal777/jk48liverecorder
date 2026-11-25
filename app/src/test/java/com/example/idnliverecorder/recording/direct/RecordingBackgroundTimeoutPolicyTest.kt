package com.example.idnliverecorder.recording.direct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingBackgroundTimeoutPolicyTest {

    @Test
    fun gracefulStopIsScheduledBeforePlatformLimit() {
        val policy = RecordingBackgroundTimeoutPolicy(
            platformLimitMs = 1_000L,
            finalizationBufferMs = 100L
        )

        policy.onAppBackgrounded(nowMs = 500L)

        assertEquals(900L, policy.millisUntilGracefulStop(nowMs = 500L))
        assertEquals(0L, policy.millisUntilGracefulStop(nowMs = 1_400L))
    }

    @Test
    fun foregroundingClearsTheBackgroundDeadline() {
        val policy = RecordingBackgroundTimeoutPolicy(
            platformLimitMs = 1_000L,
            finalizationBufferMs = 100L
        )
        policy.onAppBackgrounded(nowMs = 0L)

        policy.onAppForegrounded()

        assertNull(policy.millisUntilGracefulStop(nowMs = 500L))
    }

    @Test
    fun backgroundingAgainStartsANewDeadline() {
        val policy = RecordingBackgroundTimeoutPolicy(
            platformLimitMs = 1_000L,
            finalizationBufferMs = 100L
        )
        policy.onAppBackgrounded(nowMs = 0L)
        policy.onAppForegrounded()

        policy.onAppBackgrounded(nowMs = 2_000L)

        assertEquals(900L, policy.millisUntilGracefulStop(nowMs = 2_000L))
    }
}
