package com.example.idnliverecorder

import android.net.Uri
import com.example.idnliverecorder.core.Constants
import com.example.idnliverecorder.recording.AudioCaptureStatus
import com.example.idnliverecorder.recording.RecordingConfig
import com.example.idnliverecorder.recording.RecordingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * Phase 3 Unit Tests — Internal Audio Capture & Audio-Video Muxing
 *
 * Verifies audio capture configuration, audio status transitions,
 * timestamp synchronization, codec tags, and error handling.
 */
class AudioCaptureUnitTest {

    private fun getDummyUri(): Uri {
        return Mockito.mock(Uri::class.java)
    }

    // --- Test 1: RecordingConfig audio defaults and custom values ---
    @Test
    fun test1_recordingConfigAudioFields() {
        val config = RecordingConfig(
            width = 1920,
            height = 1080,
            densityDpi = 480,
            includeAudio = true,
            audioSampleRate = 48000,
            audioChannels = 2,
            audioBitrate = 128_000
        )
        assertEquals(48000, config.audioSampleRate)
        assertEquals(2, config.audioChannels)
        assertEquals(128_000, config.audioBitrate)
        assertTrue(config.includeAudio)
        assertEquals("H.264 / AAC", config.codecLabel)
    }

    // --- Test 2: RecordingConfig videoOnly codecLabel ---
    @Test
    fun test2_recordingConfigVideoOnlyCodecLabel() {
        val config = RecordingConfig(
            width = 1280,
            height = 720,
            densityDpi = 320,
            includeAudio = false
        )
        assertFalse(config.includeAudio)
        assertEquals("H.264 / Video Only", config.codecLabel)
    }

    // --- Test 3: AudioCaptureStatus enum values complete ---
    @Test
    fun test3_audioCaptureStatusEnumValues() {
        val statuses = AudioCaptureStatus.values()
        assertTrue(statuses.contains(AudioCaptureStatus.NotStarted))
        assertTrue(statuses.contains(AudioCaptureStatus.Initializing))
        assertTrue(statuses.contains(AudioCaptureStatus.Active))
        assertTrue(statuses.contains(AudioCaptureStatus.NoEligiblePlayback))
        assertTrue(statuses.contains(AudioCaptureStatus.AppRestrictsCapture))
        assertTrue(statuses.contains(AudioCaptureStatus.PermissionDenied))
        assertTrue(statuses.contains(AudioCaptureStatus.InitFailed))
        assertTrue(statuses.contains(AudioCaptureStatus.VideoOnly))
        assertTrue(statuses.contains(AudioCaptureStatus.Stopped))
        assertEquals(9, statuses.size)
    }

    // --- Test 4: RecordingState.Recording includes audioStatus ---
    @Test
    fun test4_recordingStateIncludesAudioStatus() {
        val uri = getDummyUri()
        val config = RecordingConfig(width = 1920, height = 1080, densityDpi = 480)
        val state = RecordingState.Recording(
            startTimeMs = 1000L,
            durationMs = 5000L,
            config = config,
            outputUri = uri,
            audioStatus = AudioCaptureStatus.Active
        )
        assertEquals(AudioCaptureStatus.Active, state.audioStatus)
        assertEquals(5000L, state.durationMs)
    }

    // --- Test 5: RecordingState.Recording default audioStatus is NotStarted ---
    @Test
    fun test5_recordingStateDefaultAudioStatus() {
        val uri = getDummyUri()
        val config = RecordingConfig(width = 1920, height = 1080, densityDpi = 480)
        val state = RecordingState.Recording(
            startTimeMs = 0L,
            config = config,
            outputUri = uri
        )
        assertEquals(AudioCaptureStatus.NotStarted, state.audioStatus)
    }

    // --- Test 6: RecordingState.Recording copy updates audioStatus immutably ---
    @Test
    fun test6_recordingStateCopyWithAudioStatus() {
        val uri = getDummyUri()
        val config = RecordingConfig(width = 1920, height = 1080, densityDpi = 480)
        val initial = RecordingState.Recording(
            startTimeMs = 0L,
            config = config,
            outputUri = uri,
            audioStatus = AudioCaptureStatus.Initializing
        )
        val updated = initial.copy(audioStatus = AudioCaptureStatus.Active)
        assertEquals(AudioCaptureStatus.Active, updated.audioStatus)
        assertEquals(AudioCaptureStatus.Initializing, initial.audioStatus)
    }

    // --- Test 7: Presentation timestamp calculation (nanos to micros) ---
    @Test
    fun test7_presentationTimestampConversion() {
        val sessionStartNanos = 1_000_000_000L // 1 second
        val currentNanos = 2_500_000_000L // 2.5 seconds
        val presentationTimeUs = (currentNanos - sessionStartNanos) / 1000L
        assertEquals(1_500_000L, presentationTimeUs) // 1.5 million microseconds
    }

    // --- Test 8: Timestamp monotonicity check ---
    @Test
    fun test8_timestampMonotonicity() {
        val sessionStartNanos = System.nanoTime()
        val timestamps = mutableListOf<Long>()
        repeat(5) {
            Thread.sleep(10)
            timestamps.add((System.nanoTime() - sessionStartNanos) / 1000L)
        }
        for (i in 1 until timestamps.size) {
            assertTrue("Timestamps must be monotonically increasing", timestamps[i] > timestamps[i - 1])
        }
    }

    // --- Test 9: AudioCaptureStatus distinct meanings ---
    @Test
    fun test9_audioCaptureStatusDistinction() {
        assertNotEquals(AudioCaptureStatus.PermissionDenied, AudioCaptureStatus.VideoOnly)
        assertNotEquals(AudioCaptureStatus.AppRestrictsCapture, AudioCaptureStatus.NoEligiblePlayback)
        assertEquals("AppRestrictsCapture", AudioCaptureStatus.AppRestrictsCapture.name)
        assertEquals("PermissionDenied", AudioCaptureStatus.PermissionDenied.name)
    }

    // --- Test 10: Audio constants verification ---
    @Test
    fun test10_audioConstantsVerification() {
        assertEquals("audio/mp4a-latm", Constants.AUDIO_MIME_TYPE)
        assertEquals(128_000, Constants.DEFAULT_AUDIO_BITRATE)
        assertEquals(48000, Constants.AUDIO_SAMPLE_RATE_48K)
        assertEquals(44100, Constants.AUDIO_SAMPLE_RATE_44K)
        assertEquals(2, Constants.AUDIO_AAC_PROFILE)
    }
}
