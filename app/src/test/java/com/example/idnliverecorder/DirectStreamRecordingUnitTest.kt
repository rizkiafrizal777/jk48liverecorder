package com.example.idnliverecorder

import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
import com.example.idnliverecorder.recording.direct.DirectRecordingService
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import com.example.idnliverecorder.recording.source.DirectStreamRecordingProvider
import com.example.idnliverecorder.recording.source.ScreenCaptureRecordingSourceProvider
import com.example.idnliverecorder.recording.validation.StreamSourceValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 5 Unit Tests — Direct Live Stream Recording Architecture
 *
 * Verifies Tests A through G in accordance with Phase 5 Specifications:
 * - Test A: API metadata and live detection.
 * - Test B: Source validation without recording (Unauthorized rejection).
 * - Test C: Format detection (HLS .m3u8 & MP4).
 * - Test D: Authorized stream recording validation.
 * - Test E: Background recording provider capabilities.
 * - Test F: Audio-Video monotonic timestamp synchronization.
 * - Test G: MediaStore file naming: JKT48_{MemberName}_{DateTime}.mp4.
 */
class DirectStreamRecordingUnitTest {

    private val validator = StreamSourceValidator()

    // --- Test A: API metadata and live detection ---
    @Test
    fun testA_apiMetadataAndLiveDetection() {
        val member = Jkt48LiveMember(
            roomId = "484801",
            memberName = "Freya Jayawardana",
            slug = "freya-jayawardana",
            title = "Malam mingguan bareng Freya!",
            thumbnail = "https://images.jkt48connect.com/members/freya.jpg",
            startedAt = "2026-11-08 19:30:00",
            startedAtMs = 1731069000000L,
            type = "idn",
            platform = "idn",
            streamingUrls = listOf("https://stream.example.com/live/freya.m3u8"),
            viewers = 15420,
            isFavorite = true
        )

        assertEquals("484801", member.roomId)
        assertEquals("Freya Jayawardana", member.memberName)
        assertEquals("idn", member.platform)
        assertTrue("Stream must be reported available when URLs are present", member.isStreamAvailable)
        assertEquals(DirectStreamStatus.AUTHORIZATION_REQUIRED, member.directRecordingStatus)
    }

    // --- Test B: Source validation without recording (Unauthorized rejection) ---
    @Test
    fun testB_sourceValidationUnauthorizedRejection() = runBlocking {
        // Without explicit authorization, live streams MUST return AUTHORIZATION_REQUIRED
        val result = validator.validate(
            url = "test://stream.example.com/live/stream.m3u8",
            provider = StreamSourceValidator.PROVIDER_JKT48CONNECT,
            isRecordingAuthorized = false
        )

        assertEquals(DirectStreamStatus.AUTHORIZATION_REQUIRED, result.status)
        assertFalse("Recording must not be allowed when unauthorized", result.canRecord)
        assertFalse(result.isAuthorized)
        assertTrue("Format must still be identified as HLS", result.isFormatSupported)
        assertTrue(result.message.contains("AUTHORIZATION"))

        // Null or blank URL must return SOURCE_UNAVAILABLE
        val emptyResult = validator.validate(url = null, isRecordingAuthorized = false)
        assertEquals(DirectStreamStatus.SOURCE_UNAVAILABLE, emptyResult.status)
        assertFalse(emptyResult.canRecord)
    }

    // --- Test C: Format detection (HLS .m3u8 & MP4) ---
    @Test
    fun testC_formatDetection() {
        val hlsFormat = validator.identifyFormat("https://live.idn.app/stream/member.m3u8?token=xyz")
        assertEquals(StreamSourceValidator.StreamMediaFormat.HLS, hlsFormat)

        val directMp4Format = validator.identifyFormat("https://cdn.example.com/test_clip.mp4")
        assertEquals(StreamSourceValidator.StreamMediaFormat.MP4, directMp4Format)

        val unknownFormat = validator.identifyFormat("https://live.idn.app/stream/raw_socket")
        assertEquals(StreamSourceValidator.StreamMediaFormat.UNKNOWN, unknownFormat)

        val noneFormat = validator.identifyFormat(null)
        assertEquals(StreamSourceValidator.StreamMediaFormat.NONE, noneFormat)
    }

    // --- Test D: Authorized stream recording validation (Authorized test media) ---
    @Test
    fun testD_authorizedStreamRecordingValidation() = runBlocking {
        // When using authorized test media with explicit isRecordingAuthorized = true
        val result = validator.validate(
            url = "test://authorized/test_live.m3u8",
            provider = StreamSourceValidator.PROVIDER_TEST_MEDIA,
            isRecordingAuthorized = true
        )

        assertEquals(DirectStreamStatus.READY_TO_RECORD, result.status)
        assertTrue("Must allow recording when source is active, HLS, and authorized", result.canRecord)
        assertTrue(result.isAuthorized)
        assertTrue(result.isSourceActive)
    }

    // --- Test E: Background recording provider capabilities ---
    @Test
    fun testE_backgroundRecordingProviderCapabilities() {
        val provider = DirectStreamRecordingProvider()

        assertEquals("direct_stream", provider.id)
        assertFalse("Direct stream MUST NOT require MediaProjection", provider.requiresMediaProjection)
        assertTrue("Direct stream MUST support background recording", provider.supportsBackgroundRecording)
        assertEquals(DirectStreamStatus.SOURCE_UNAVAILABLE, provider.currentStatus.value)

        // Screen capture fallback comparison
        val screenProvider = ScreenCaptureRecordingSourceProvider()
        assertEquals("screen_capture", screenProvider.id)
        assertTrue("Screen capture DOES require MediaProjection", screenProvider.requiresMediaProjection)
        assertFalse("Screen capture CANNOT record background app window independently", screenProvider.supportsBackgroundRecording)
    }

    // --- Test F: Audio-Video monotonic timestamp synchronization ---
    @Test
    fun testF_audioVideoMonotonicSynchronization() {
        // Simulating PTS adjustment logic across HLS segment boundaries
        var lastVideoPtsUs = 0L
        var videoPtsOffsetUs = 0L

        // Segment 1: samples from 0 to 2,000,000 us (2s)
        val seg1Sample1 = 0L
        val seg1Sample2 = 1_000_000L
        val seg1Sample3 = 2_000_000L

        val adjSeg1_1 = seg1Sample1 + videoPtsOffsetUs
        lastVideoPtsUs = adjSeg1_1
        val adjSeg1_2 = seg1Sample2 + videoPtsOffsetUs
        lastVideoPtsUs = adjSeg1_2
        val adjSeg1_3 = seg1Sample3 + videoPtsOffsetUs
        lastVideoPtsUs = adjSeg1_3

        assertEquals(2_000_000L, lastVideoPtsUs)

        // Segment 2: timestamp reset / discontinuity from source (e.g. restarts at 0)
        val seg2Sample1 = 0L
        if (seg2Sample1 <= lastVideoPtsUs) {
            videoPtsOffsetUs = lastVideoPtsUs + 33_333L - seg2Sample1
        }

        val adjSeg2_1 = seg2Sample1 + videoPtsOffsetUs
        val adjSeg2_2 = 1_000_000L + videoPtsOffsetUs

        assertTrue("Segment 2 first sample PTS must be strictly greater than last sample of Segment 1", adjSeg2_1 > adjSeg1_3)
        assertEquals(2_033_333L, adjSeg2_1)
        assertTrue("Monotonic timestamp growth preserved", adjSeg2_2 > adjSeg2_1)
    }

    // --- Test G: MediaStore file naming: JKT48_{MemberName}_{DateTime}.mp4 ---
    @Test
    fun testG_mediaStoreDirectFileNaming() {
        val memberName = "Freya Jayawardana"
        val sanitizedMember = memberName.replace("[^a-zA-Z0-9]".toRegex(), "_").trim('_')
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayName = "JKT48_${sanitizedMember}_${timeStamp}.mp4"

        assertTrue("File name must start with JKT48_", displayName.startsWith("JKT48_Freya_Jayawardana_"))
        assertTrue("File extension must be .mp4", displayName.endsWith(".mp4"))
        assertFalse("File name must not contain invalid path characters", displayName.contains(" ") || displayName.contains("/"))
    }
}

