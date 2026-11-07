package com.example.idnliverecorder

import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.recording.RecordingSessionModel
import com.example.idnliverecorder.recording.SessionStatus
import com.example.idnliverecorder.recording.source.LiveSourceCapabilities
import com.example.idnliverecorder.recording.source.LiveSourceResult
import com.example.idnliverecorder.recording.source.LiveSourceType
import com.example.idnliverecorder.recording.source.OfficialStreamProvider
import com.example.idnliverecorder.recording.source.ScreenCaptureProvider
import com.example.idnliverecorder.recording.source.UnsupportedSourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 Unit Tests — Modular Live Source Provider Architecture
 *
 * Verifies provider capabilities, official stream rejection, session metadata persistence,
 * provider switching, and audio-video independence.
 */
class LiveSourceProviderUnitTest {

    // --- Test 1: ScreenCaptureProvider capabilities ---
    @Test
    fun test1_screenCaptureProviderCapabilities() {
        val provider = ScreenCaptureProvider()
        val caps = provider.capabilities

        assertEquals(LiveSourceType.FullScreenRecording, provider.type)
        assertTrue("ScreenCapture requires MediaProjection", caps.requiresMediaProjection)
        assertTrue("ScreenCapture records other applications", caps.recordsOtherApplications)
        assertTrue("ScreenCapture records UI elements", caps.recordsUiElements)
        assertFalse("ScreenCapture cannot capture non-rendered background video", caps.supportsBackgroundRecording)
        assertEquals("Full Screen Recording", caps.displayName)
    }

    // --- Test 2: OfficialStreamProvider is unavailable by default ---
    @Test
    fun test2_officialStreamProviderUnavailableByDefault() {
        val provider = OfficialStreamProvider()
        val caps = provider.capabilities

        assertEquals(LiveSourceType.OfficialLiveStream, provider.type)
        assertFalse("Official stream is never claimed available without authorization", caps.isAvailableOnDevice)
        assertFalse("Direct stream does not require MediaProjection screen capture", caps.requiresMediaProjection)
        assertFalse("Direct stream has no screen UI elements", caps.recordsUiElements)
        assertEquals("Official live stream integration is not available.", caps.availabilityNotice)
    }

    // --- Test 3: OfficialStreamProvider prepare() returns UNSUPPORTED_OFFICIAL_SOURCE ---
    @Test
    fun test3_officialStreamProviderReturnsUnsupportedOfficialSource() {
        val provider = OfficialStreamProvider(isAuthorizedIntegrationActive = false)
        val dummyContext = org.mockito.Mockito.mock(android.content.Context::class.java)

        val result = provider.prepare(
            context = dummyContext,
            streamerName = "Freya",
            streamerId = null
        )

        assertTrue(result is LiveSourceResult.UnsupportedOfficialSource)
        val unsupported = result as LiveSourceResult.UnsupportedOfficialSource
        assertEquals(OfficialStreamProvider.CODE_UNSUPPORTED, unsupported.code)
        assertEquals(OfficialStreamProvider.MESSAGE_UNSUPPORTED, unsupported.message)
    }

    // --- Test 4: OfficialStreamProvider startVideoSource rejects when unauthorized ---
    @Test
    fun test4_officialStreamProviderStartVideoSourceRejects() {
        val provider = OfficialStreamProvider(isAuthorizedIntegrationActive = false)
        val dummySurface = org.mockito.Mockito.mock(android.view.Surface::class.java)
        val dummyHandler = org.mockito.Mockito.mock(android.os.Handler::class.java)
        val config = com.example.idnliverecorder.recording.RecordingConfig(
            width = 1920,
            height = 1080,
            densityDpi = 480
        )

        val started = provider.startVideoSource(
            mediaProjection = null,
            config = config,
            targetSurface = dummySurface,
            mainHandler = dummyHandler
        )
        assertFalse(started)
        assertFalse(provider.isVideoSourceActive)
    }

    // --- Test 5: UnsupportedSourceProvider prepare() returns DeviceNotSupported ---
    @Test
    fun test5_unsupportedSourceProviderFails() {
        val provider = UnsupportedSourceProvider(failureReason = "Device incompatible")
        val dummyContext = org.mockito.Mockito.mock(android.content.Context::class.java)

        val result = provider.prepare(dummyContext, null, null)
        assertTrue(result is LiveSourceResult.DeviceNotSupported)
        assertEquals("Device incompatible", (result as LiveSourceResult.DeviceNotSupported).message)
    }

    // --- Test 6: RecordingSessionModel metadata from LiveDetection ---
    @Test
    fun test6_recordingSessionModelFromDetection() {
        val detected = DetectedLiveItem(
            id = "det-123",
            streamerName = "Freya Jayawardana",
            notificationContent = "Sedang live streaming sekarang!",
            detectedAtMs = 1700000000000L,
            packageName = "com.idn.app",
            matchedKeyword = "sedang live"
        )

        val model = RecordingSessionModel.fromDetection(
            item = detected,
            provider = LiveSourceType.FullScreenRecording
        )

        assertNotNull(model.sessionId)
        assertEquals("Freya Jayawardana", model.streamerName)
        assertNull("Never assume streamer name is a valid stream identifier", model.streamerId)
        assertEquals(1700000000000L, model.detectionTimestamp)
        assertEquals(LiveSourceType.FullScreenRecording, model.selectedProvider)
        assertEquals(SessionStatus.Preparing, model.status)
        assertEquals(0L, model.durationMs)
    }

    // --- Test 7: RecordingSessionModel handles null detection gracefully ---
    @Test
    fun test7_recordingSessionModelNullDetection() {
        val model = RecordingSessionModel.fromDetection(null)

        assertNotNull(model.sessionId)
        assertNull(model.streamerName)
        assertNull(model.streamerId)
        assertNull(model.detectionTimestamp)
        assertEquals(LiveSourceType.FullScreenRecording, model.selectedProvider)
        assertEquals(SessionStatus.Preparing, model.status)
    }

    // --- Test 8: RecordingSessionModel copy updates status and duration ---
    @Test
    fun test8_recordingSessionModelCopy() {
        val initial = RecordingSessionModel(
            sessionId = "sess-1",
            streamerName = "Christy",
            selectedProvider = LiveSourceType.FullScreenRecording,
            status = SessionStatus.Preparing
        )

        val updated = initial.copy(
            status = SessionStatus.Active,
            durationMs = 15000L,
            isVideoActive = true
        )

        assertEquals("sess-1", updated.sessionId)
        assertEquals(SessionStatus.Active, updated.status)
        assertEquals(15000L, updated.durationMs)
        assertTrue(updated.isVideoActive)
        assertEquals(SessionStatus.Preparing, initial.status) // Immutability
    }

    // --- Test 9: ScreenCaptureProvider audio state marking ---
    @Test
    fun test9_screenCaptureProviderAudioMarking() {
        val provider = ScreenCaptureProvider()
        assertFalse(provider.isAudioSourceActive)

        provider.markAudioActive(true)
        assertTrue(provider.isAudioSourceActive)

        provider.stop()
        assertFalse(provider.isAudioSourceActive)
        assertFalse(provider.isVideoSourceActive)
    }

    // --- Test 10: Provider capabilities Android 13 (API 33) compatibility on Redmi 10C ---
    @Test
    fun test10_redmi10cAndroid13Compatibility() {
        // Redmi 10C runs Android 13 (API 33)
        val redmiSdk = 33
        val screenCaps = LiveSourceCapabilities.forFullScreen(currentSdk = redmiSdk)
        assertTrue("Full Screen Recording must be available on Android 13", screenCaps.isAvailableOnDevice)
        assertEquals(29, screenCaps.minSdk)

        val officialCaps = LiveSourceCapabilities.forOfficialStream()
        assertFalse("Official stream must not be claimed available on any SDK without integration", officialCaps.isAvailableOnDevice)
    }
}
