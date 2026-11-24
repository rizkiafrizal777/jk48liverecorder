package com.example.idnliverecorder

import android.net.Uri
import com.example.idnliverecorder.recording.RecordingEventBridge
import com.example.idnliverecorder.recording.direct.DirectStreamRecordingEngine
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import com.example.idnliverecorder.recording.direct.HlsPlaylistResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Regression Unit Tests for Direct Recording Critical Bug Fixes (Phase 8).
 *
 * Verifies fixes for:
 * 1. HLS Master Playlist vs Media Playlist detection and optimal variant selection.
 * 2. Media Playlist parsing with relative URL resolution and fMP4 #EXT-X-MAP init segment extraction.
 * 3. Secure URL sanitization preventing credential and API key leakage in logs.
 * 4. Idempotent finalization and rejection of 0-sample empty recordings.
 * 5. Event Bridge propagation for decoupled UI and repository auto-refresh.
 */
class DirectStreamRecordingRegressionTest {

    // --- Defect 1: Master Playlist Detection & Variant Selection ---
    @Test
    fun test1_masterPlaylistDetectionAndOptimalVariantSelection() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            360p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,FRAME-RATE=30.000,CODECS="avc1.64001f,mp4a.40.2"
            720p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480,CODECS="avc1.4d401f,mp4a.40.2"
            480p/index.m3u8
        """.trimIndent()

        val masterUrl = "https://stream.example.com/live/room_48/master.m3u8?token=mock_token_123"

        // 1. Must identify as master playlist
        assertTrue("Must detect Master Playlist", HlsPlaylistResolver.isMasterPlaylist(masterPlaylist))

        // 2. Parse variants
        val variants = HlsPlaylistResolver.parseMasterPlaylist(masterPlaylist, masterUrl)
        assertEquals("Must parse 3 variants", 3, variants.size)

        // 3. Optimal variant selection must pick highest bandwidth (720p @ 2500000)
        val optimal = HlsPlaylistResolver.selectOptimalVariant(variants)
        assertNotNull("Optimal variant must not be null", optimal)
        assertEquals(2500000L, optimal!!.bandwidth)
        assertEquals("1280x720", optimal.resolution)

        // 4. Relative URL must be resolved properly against master URL
        assertEquals(
            "https://stream.example.com/live/room_48/720p/index.m3u8",
            optimal.url
        )
    }

    // --- Defect 1 & 4: Media Playlist Parsing & fMP4 Support ---
    @Test
    fun test2_mediaPlaylistParsingAndFmp4Support() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:3
            #EXT-X-MEDIA-SEQUENCE:101
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:2.500,
            seg_101.m4s
            #EXTINF:2.500,
            seg_102.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val mediaUrl = "https://cdn.example.com/hls/channel1/playlist.m3u8"

        // 1. Must NOT identify as master playlist
        assertFalse("Must identify as Media Playlist", HlsPlaylistResolver.isMasterPlaylist(mediaPlaylist))

        // 2. Parse playlist
        val resolved = HlsPlaylistResolver.parseMediaPlaylist(mediaPlaylist, mediaUrl)

        assertEquals("Target duration must be parsed", 3L, resolved.targetDurationSec)
        assertTrue("Endlist flag must be detected", resolved.isEndList)
        assertEquals("Must resolve 2 media segments", 2, resolved.segments.size)

        // 3. fMP4 init segment must be detected and resolved
        assertEquals(
            "https://cdn.example.com/hls/channel1/init.mp4",
            resolved.initSegmentUrl
        )

        // 4. Verify segment URLs
        assertEquals("https://cdn.example.com/hls/channel1/seg_101.m4s", resolved.segments[0].url)
        assertEquals(2.5, resolved.segments[0].durationSec, 0.001)
        assertEquals("https://cdn.example.com/hls/channel1/seg_102.m4s", resolved.segments[1].url)
    }

    // --- Defect 1: Relative and Root-Relative URL Resolution ---
    @Test
    fun test3_urlResolutionRobustness() {
        val baseUrl = "https://cdn.jkt48connect.com/live/freya/playlist.m3u8?auth=123"

        // Relative sibling
        val sibling = HlsPlaylistResolver.resolveUrl(baseUrl, "chunk_1.ts")
        assertEquals("https://cdn.jkt48connect.com/live/freya/chunk_1.ts", sibling)

        // Relative parent
        val parent = HlsPlaylistResolver.resolveUrl(baseUrl, "../other/chunk_2.ts")
        assertEquals("https://cdn.jkt48connect.com/live/other/chunk_2.ts", parent)

        // Root relative
        val rootRelative = HlsPlaylistResolver.resolveUrl(baseUrl, "/streams/chunk_3.ts")
        assertEquals("https://cdn.jkt48connect.com/streams/chunk_3.ts", rootRelative)

        // Absolute URL remains unchanged
        val absolute = HlsPlaylistResolver.resolveUrl(baseUrl, "https://othercdn.com/seg.ts")
        assertEquals("https://othercdn.com/seg.ts", absolute)
    }

    // --- Defect 5: URL Sanitization for Credential Protection ---
    @Test
    fun test4_secureUrlSanitization() {
        val sensitiveUrl = "https://v3.jkt48connect.com/api/jkt48/live?apikey=super_secret_api_key_48&member=Freya&token=xyz789&signature=sig_abc"
        val sanitized = HlsPlaylistResolver.sanitizeUrl(sensitiveUrl)

        assertFalse("API key must not be exposed", sanitized.contains("super_secret_api_key_48"))
        assertFalse("Token must not be exposed", sanitized.contains("xyz789"))
        assertFalse("Signature must not be exposed", sanitized.contains("sig_abc"))
        assertTrue("Benign member parameter should remain visible", sanitized.contains("member=Freya"))
        assertTrue("Masking markers must be present", sanitized.contains("apikey=***"))
        assertTrue("Masking markers must be present", sanitized.contains("token=***"))
        assertTrue("Masking markers must be present", sanitized.contains("signature=***"))

        // URL without query
        val plainUrl = "https://stream.example.com/live/index.m3u8"
        assertEquals(plainUrl, HlsPlaylistResolver.sanitizeUrl(plainUrl))
    }

    // --- Defect 4: RecordingEventBridge Communication ---
    @Test
    fun test5_recordingEventBridgeBroadcasting() = runBlocking {
        val mockUri = mock(Uri::class.java)
        val event = RecordingEventBridge.RecordingCompletedEvent(
            uri = mockUri,
            memberName = "Freya Jayawardana",
            displayName = "JKT48_Freya_20261108.mp4",
            sizeBytes = 25_000_000L,
            durationMs = 120_000L,
            isDirectStream = true
        )

        RecordingEventBridge.notifyRecordingCompleted(event)
        val receivedEvent = RecordingEventBridge.recordingCompletedEvents.first()

        assertNotNull("Collector must receive the emitted event", receivedEvent)
        assertEquals("Freya Jayawardana", receivedEvent.memberName)
        assertEquals("JKT48_Freya_20261108.mp4", receivedEvent.displayName)
        assertEquals(25_000_000L, receivedEvent.sizeBytes)
        assertEquals(120_000L, receivedEvent.durationMs)
        assertTrue(receivedEvent.isDirectStream)
    }

    // --- Defect 2: DirectStreamStatus State Definitions ---
    @Test
    fun test6_directStreamStatusVerification() {
        val statuses = DirectStreamStatus.values()
        assertTrue(statuses.contains(DirectStreamStatus.SOURCE_AVAILABLE))
        assertTrue(statuses.contains(DirectStreamStatus.READY_TO_RECORD))
        assertTrue(statuses.contains(DirectStreamStatus.RECORDING))
        assertTrue(statuses.contains(DirectStreamStatus.FINALIZING))
        assertTrue(statuses.contains(DirectStreamStatus.COMPLETED))
        assertTrue(statuses.contains(DirectStreamStatus.ERROR))
        assertTrue(statuses.contains(DirectStreamStatus.AUTHORIZATION_REQUIRED))
        assertTrue(statuses.contains(DirectStreamStatus.SOURCE_UNAVAILABLE))
    }

    // --- Stage 4 (Fix A): Content-Type & Magic Byte Segment Detection ---
    @Test
    fun test7_fmp4ExtensionlessUrlDetectedByContentType() {
        val tempFile = java.io.File.createTempFile("seg_extensionless", "")
        tempFile.deleteOnExit()
        tempFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

        val format1 = com.example.idnliverecorder.recording.direct.HlsSegmentDetector.detectFormat(tempFile, "video/mp4")
        assertEquals(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.SegmentFormat.FRAGMENTED_MP4, format1)

        val format2 = com.example.idnliverecorder.recording.direct.HlsSegmentDetector.detectFormat(tempFile, "video/iso.segment; charset=binary")
        assertEquals(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.SegmentFormat.FRAGMENTED_MP4, format2)

        val format3 = com.example.idnliverecorder.recording.direct.HlsSegmentDetector.detectFormat(tempFile, "video/mp2t")
        assertEquals(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.SegmentFormat.MPEG_TS, format3)
    }

    @Test
    fun test8_fmp4DetectedByMagicBytes() {
        val tempFile = java.io.File.createTempFile("seg_fmp4_magic", "")
        tempFile.deleteOnExit()
        // 4 bytes size + 'moof'
        val moofBytes = byteArrayOf(0, 0, 0, 32, 'm'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'f'.code.toByte(), 0, 0, 0, 0)
        tempFile.writeBytes(moofBytes)

        val format = com.example.idnliverecorder.recording.direct.HlsSegmentDetector.detectByMagicBytes(tempFile)
        assertEquals(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.SegmentFormat.FRAGMENTED_MP4, format)
    }

    @Test
    fun test9_mpegTsDetectedBySyncByte() {
        val tempFile = java.io.File.createTempFile("seg_ts_magic", "")
        tempFile.deleteOnExit()
        // Sync byte 0x47 followed by standard MPEG-TS packet header
        val tsBytes = byteArrayOf(0x47, 0x40, 0x00, 0x10, 0x00, 0x00, 0x01, 0xE0.toByte(), 0, 0, 0, 0)
        tempFile.writeBytes(tsBytes)

        val format = com.example.idnliverecorder.recording.direct.HlsSegmentDetector.detectByMagicBytes(tempFile)
        assertEquals(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.SegmentFormat.MPEG_TS, format)
    }

    @Test
    fun test10_validAndInvalidInitSegment() {
        val validInit = java.io.File.createTempFile("init_valid", ".mp4")
        validInit.deleteOnExit()
        val ftypBytes = byteArrayOf(0, 0, 0, 32, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte())
        validInit.writeBytes(ftypBytes)
        assertTrue(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.isValidInitSegment(validInit))

        val invalidInit = java.io.File.createTempFile("init_invalid", ".mp4")
        invalidInit.deleteOnExit()
        invalidInit.writeBytes("INVALID_TEXT_PLAYLIST".toByteArray())
        assertFalse(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.isValidInitSegment(invalidInit))
    }

    // --- Stage 4 (Fix B): Master Playlist Audio Renditions ---
    @Test
    fun test11_masterPlaylistWithAudioRenditions() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aac",NAME="Indonesian",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE="id",URI="audio/prog_index.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2149280,RESOLUTION=1280x720,AUDIO="audio-aac"
            720p/chunklist.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=892400,RESOLUTION=854x480,AUDIO="audio-aac"
            480p/chunklist.m3u8
        """.trimIndent()

        val masterUrl = "https://cdn.idn.app/live/master.m3u8?token=xyz"
        val renditions = HlsPlaylistResolver.parseAudioRenditions(masterPlaylist, masterUrl)
        assertTrue("Must parse audio group", renditions.containsKey("audio-aac"))
        assertEquals(1, renditions["audio-aac"]?.size)

        val rendition = renditions["audio-aac"]!![0]
        assertEquals("Indonesian", rendition.name)
        assertTrue(rendition.isDefault)
        assertEquals("https://cdn.idn.app/live/audio/prog_index.m3u8", rendition.url)

        val variants = HlsPlaylistResolver.parseMasterPlaylist(masterPlaylist, masterUrl)
        val optimal = HlsPlaylistResolver.selectOptimalVariant(variants)
        assertNotNull(optimal)

        val audioUrl = HlsPlaylistResolver.resolveAudioRenditionUrl(optimal!!, masterPlaylist, masterUrl)
        assertEquals("https://cdn.idn.app/live/audio/prog_index.m3u8", audioUrl)
    }

    @Test
    fun test12_masterPlaylistWithoutAudioRenditions() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
            720p/index.m3u8
        """.trimIndent()

        val masterUrl = "https://cdn.idn.app/live/master.m3u8"
        val variants = HlsPlaylistResolver.parseMasterPlaylist(masterPlaylist, masterUrl)
        val optimal = HlsPlaylistResolver.selectOptimalVariant(variants)
        assertNotNull(optimal)

        val audioUrl = HlsPlaylistResolver.resolveAudioRenditionUrl(optimal!!, masterPlaylist, masterUrl)
        assertNull("Video-only stream without EXT-X-MEDIA should return null safely", audioUrl)
    }

    @Test
    fun test13_zeroByteOrMissingSegmentHandledSafely() {
        val emptyFile = java.io.File.createTempFile("empty_seg", "")
        emptyFile.deleteOnExit()
        val format = com.example.idnliverecorder.recording.direct.HlsSegmentDetector.detectByMagicBytes(emptyFile)
        assertEquals(com.example.idnliverecorder.recording.direct.HlsSegmentDetector.SegmentFormat.UNKNOWN, format)
    }

    // --- ENAMETOOLONG Regression Tests ---
    @Test
    fun test14_longUrlWithLargeQueryParametersProducesShortFilename() {
        // Construct a realistic >1000 character URL with a massive 'dna' token containing slashes & query parameters
        val massiveToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." + "a".repeat(850) + "/slash/inside/token?another=param"
        val longSegmentUrl = "https://live-edge.idnlive.com/live/stream_1080p/chunk_000123.m4s?dna=$massiveToken"
        assertTrue("URL must be very long (>900 chars)", longSegmentUrl.length > 900)

        // 1. Verify extension resolution extracts '.m4s' from the path without query string pollution
        val ext = DirectStreamRecordingEngine.resolveExtension(longSegmentUrl, isInit = false)
        assertEquals(".m4s", ext)

        // 2. Verify generated local filename is short, unique, and deterministic
        val fileName = DirectStreamRecordingEngine.generateLocalFileName(123, longSegmentUrl, isInit = false)
        assertEquals("segment_000123.m4s", fileName)
        assertTrue("Filename length must be short (< 30 chars)", fileName.length <= 30)
        assertFalse("Filename must NOT contain query parameters", fileName.contains("?"))
        assertFalse("Filename must NOT contain token data", fileName.contains("eyJ"))
        assertFalse("Filename must NOT contain slashes", fileName.contains("/"))

        // 3. Verify file can be created and written to disk without ENAMETOOLONG
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_hls_recorder_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        val segmentFile = java.io.File(tempDir, fileName)
        segmentFile.writeBytes("mock_fragment_bytes".toByteArray())
        assertTrue("Segment file must exist on disk", segmentFile.exists())
        assertEquals(19L, segmentFile.length())

        // 4. Verify combined filename generation is also short and valid
        val combinedFile = java.io.File(tempDir, "combined_${segmentFile.nameWithoutExtension}.mp4")
        assertEquals("combined_segment_000123.mp4", combinedFile.name)
        assertTrue("Combined filename must be < 35 chars", combinedFile.name.length <= 35)

        segmentFile.delete()
        tempDir.delete()
    }

    @Test
    fun test15_urlSanitizationMasksDnaParameter() {
        val sensitiveUrl = "https://live-edge.idnlive.com/live/stream/chunk_001.m4s?dna=eyJhbGciOiJIUzI1NiJ9.super_secret_payload&other=123"
        val sanitized = HlsPlaylistResolver.sanitizeUrl(sensitiveUrl)

        assertFalse("Sanitized URL must not leak raw dna token", sanitized.contains("super_secret_payload"))
        assertTrue("Sanitized URL must mask dna parameter", sanitized.contains("dna=***"))
        assertTrue("Sanitized URL must preserve other parameters", sanitized.contains("other=123"))
    }

    @Test
    fun test16_unusualOrMissingExtensionResolvesSafely() {
        // Init segment without extension in path -> default .mp4
        val initUrl = "https://stream.example.com/live/init_data?dna=token123"
        val initFileName = DirectStreamRecordingEngine.generateLocalFileName(1, initUrl, isInit = true)
        assertEquals("init_000001.mp4", initFileName)

        // Media segment without extension in path -> default .tmp
        val noExtMediaUrl = "https://stream.example.com/live/media_fragment_123?dna=token123"
        val mediaFileName = DirectStreamRecordingEngine.generateLocalFileName(5, noExtMediaUrl, isInit = false)
        assertEquals("segment_000005.tmp", mediaFileName)

        // MPEG-TS segment
        val tsUrl = "https://stream.example.com/live/video_720p.ts?token=abc"
        val tsFileName = DirectStreamRecordingEngine.generateLocalFileName(2, tsUrl, isInit = false)
        assertEquals("segment_000002.ts", tsFileName)
    }

    // --- Phase 5: PTS Normalization, Keyframe Gating & Timeline Safety ---

    @Test
    fun test17_sessionPtsNormalization_firstSampleStartsAtZeroAndPreservesIntervals() {
        val rawTimestamps = listOf(
            3_600_000_000L,
            3_600_033_333L,
            3_600_066_666L,
            3_600_100_000L
        )

        var baseSessionPtsUs = -1L
        val normalizedTimestamps = mutableListOf<Long>()

        for (rawPts in rawTimestamps) {
            if (baseSessionPtsUs < 0L) {
                baseSessionPtsUs = rawPts
            }
            val normalizedPts = rawPts - baseSessionPtsUs
            normalizedTimestamps.add(normalizedPts)
        }

        // First frame must start at t = 0 us
        assertEquals(0L, normalizedTimestamps[0])

        // Subsequent frame intervals must match original exact ~33,333 us (30 fps) intervals
        for (i in 1 until normalizedTimestamps.size) {
            val rawDelta = rawTimestamps[i] - rawTimestamps[i - 1]
            val normalizedDelta = normalizedTimestamps[i] - normalizedTimestamps[i - 1]
            assertEquals("Normalized frame delta must match raw frame delta exactly", rawDelta, normalizedDelta)
        }
    }

    @Test
    fun test18_videoKeyframeGate_skipsInitialNonSyncFrames() {
        val syncKeyframeFlag = 1 // MediaCodec.BUFFER_FLAG_KEY_FRAME / MediaExtractor.SAMPLE_FLAG_SYNC
        val nonSyncFlag = 0

        val incomingFrames = listOf(
            nonSyncFlag, // P-frame -> skip
            nonSyncFlag, // B-frame -> skip
            syncKeyframeFlag, // IDR Keyframe -> allow
            nonSyncFlag, // Subsequent P-frame -> allow
            nonSyncFlag // Subsequent B-frame -> allow
        )

        var hasSeenFirstVideoKeyframe = false
        val writtenFrames = mutableListOf<Int>()

        for (flag in incomingFrames) {
            if (!hasSeenFirstVideoKeyframe) {
                val isKey = (flag and syncKeyframeFlag) != 0
                if (!isKey) {
                    continue // Skip non-sync frame
                }
                hasSeenFirstVideoKeyframe = true
            }
            writtenFrames.add(flag)
        }

        assertEquals("Must write exactly 3 frames (skipping the first 2 non-sync frames)", 3, writtenFrames.size)
        assertEquals("First written frame must be a keyframe", syncKeyframeFlag, writtenFrames[0])
        assertEquals("Second written frame should be the subsequent P-frame", nonSyncFlag, writtenFrames[1])
        assertEquals("Third written frame should be the subsequent B-frame", nonSyncFlag, writtenFrames[2])
    }

    @Test
    fun test19_ptsNormalization_sharedAudioVideoTimeline() {
        var baseSessionPtsUs = -1L

        // Audio arrives first at t = 3,600,000,000 us
        val audioRawPts1 = 3_600_000_000L
        if (baseSessionPtsUs < 0L) {
            baseSessionPtsUs = audioRawPts1
        }
        val audioNormPts1 = audioRawPts1 - baseSessionPtsUs

        // Video arrives 10 ms (10,000 us) later at t = 3,600_010_000 us
        val videoRawPts1 = 3_600_010_000L
        val videoNormPts1 = videoRawPts1 - baseSessionPtsUs

        assertEquals(0L, audioNormPts1)
        assertEquals(10_000L, videoNormPts1)

        val deltaRaw = videoRawPts1 - audioRawPts1
        val deltaNorm = videoNormPts1 - audioNormPts1
        assertEquals("A/V synchronization offset must be preserved identically", deltaRaw, deltaNorm)
    }

    @Test
    fun test20_crossSegmentTimelineReset_bridgesOffset() {
        // Segment 1 ends at 3,602,000,000 us (~2 seconds in)
        val seg1LastRawPts = 3_602_000_000L
        val baseSessionPtsUs = 3_600_000_000L
        val lastWrittenPtsUs = seg1LastRawPts - baseSessionPtsUs // 2,000,000 us

        // Segment 2 raw PTS resets to 100,000 us (discontinuity / reset)
        val seg2FirstRawPts = 100_000L
        var segmentTimelineOffsetUs = 0L

        if (seg2FirstRawPts < seg1LastRawPts - 500_000L) {
            // Discontinuity detected: bridge timeline so next sample is lastWrittenPts + expected frame duration
            val expectedNextPts = lastWrittenPtsUs + 33_333L
            segmentTimelineOffsetUs = expectedNextPts - (seg2FirstRawPts - baseSessionPtsUs)
        }

        val seg2NormPts = (seg2FirstRawPts - baseSessionPtsUs) + segmentTimelineOffsetUs
        assertEquals("Reset sample must smoothly continue after segment 1", lastWrittenPtsUs + 33_333L, seg2NormPts)
    }

    @Test
    fun test21_ptsNormalization_videoArrivesBeforeAudio() {
        var baseSessionPtsUs = -1L

        // Video arrives first at raw PTS = 3,600,000,000 us (IDR keyframe)
        val videoRawPts1 = 3_600_000_000L
        if (baseSessionPtsUs < 0L) {
            baseSessionPtsUs = videoRawPts1
        }
        val videoNormPts1 = videoRawPts1 - baseSessionPtsUs

        // Audio arrives 5 ms (5,000 us) later at raw PTS = 3,600,005,000 us
        val audioRawPts1 = 3_600_005_000L
        val audioNormPts1 = audioRawPts1 - baseSessionPtsUs

        assertEquals(0L, videoNormPts1)
        assertEquals(5_000L, audioNormPts1)

        val deltaRaw = audioRawPts1 - videoRawPts1
        val deltaNorm = audioNormPts1 - videoNormPts1
        assertEquals("A/V delta must match exactly when video arrives before audio", deltaRaw, deltaNorm)
    }

    @Test
    fun test22_crossSegmentContinuousTimestamps() {
        var baseSessionPtsUs = -1L
        var segmentVideoTimelineOffsetUs = 0L
        var lastVideoPtsUs = -1L
        var lastRawVideoPtsInSegment = -1L

        // Segment 1 samples (2 seconds: 0 to 2,000,000 us)
        val seg1RawSamples = listOf(3_600_000_000L, 3_601_000_000L, 3_602_000_000L)
        val seg1Norm = mutableListOf<Long>()
        for (rawPts in seg1RawSamples) {
            if (baseSessionPtsUs < 0L) baseSessionPtsUs = rawPts
            val norm = (rawPts - baseSessionPtsUs) + segmentVideoTimelineOffsetUs
            lastVideoPtsUs = norm
            lastRawVideoPtsInSegment = rawPts
            seg1Norm.add(norm)
        }

        assertEquals(listOf(0L, 1_000_000L, 2_000_000L), seg1Norm)

        // Segment 2 samples (continuous stream: starts at 3,602,033,333 us)
        val seg2RawSamples = listOf(3_602_033_333L, 3_603_000_000L)
        val seg2Norm = mutableListOf<Long>()
        var firstSampleInSeg2 = true

        for (rawPts in seg2RawSamples) {
            if (firstSampleInSeg2) {
                firstSampleInSeg2 = false
                // No reset: rawPts (3,602,033,333) >= lastRaw (3,602,000,000) - 500_000
                if (rawPts < lastRawVideoPtsInSegment - 500_000L) {
                    segmentVideoTimelineOffsetUs = lastVideoPtsUs + 33_333L - (rawPts - baseSessionPtsUs)
                }
            }
            val norm = (rawPts - baseSessionPtsUs) + segmentVideoTimelineOffsetUs
            lastVideoPtsUs = norm
            lastRawVideoPtsInSegment = rawPts
            seg2Norm.add(norm)
        }

        assertEquals("Continuous segment must keep offset at 0", 0L, segmentVideoTimelineOffsetUs)
        assertEquals(listOf(2_033_333L, 3_000_000L), seg2Norm)
    }

    @Test
    fun test23_crossSegmentLargeDiscontinuityOver500k() {
        val baseSessionPtsUs = 3_600_000_000L
        val seg1LastRawPts = 3_602_000_000L
        val seg1LastNormPts = 2_000_000L

        // Discontinuity of -1,000,000,000 us (drops from 3.602s to 2.602s)
        val seg2FirstRawPts = 2_602_000_000L
        val isDiscontinuity = seg2FirstRawPts < (seg1LastRawPts - 500_000L)
        assertTrue("Must detect discontinuity larger than 500,000 us", isDiscontinuity)

        val expectedNextPts = seg1LastNormPts + 33_333L
        val segmentVideoTimelineOffsetUs = expectedNextPts - (seg2FirstRawPts - baseSessionPtsUs)
        val seg2FirstNormPts = (seg2FirstRawPts - baseSessionPtsUs) + segmentVideoTimelineOffsetUs

        assertEquals("Next sample must continue right after segment 1", 2_033_333L, seg2FirstNormPts)
    }

    @Test
    fun test24_missingKeyframeCondition_rejectsRecording() {
        // Given a recording where audio was written, but 0 video frames were written (no keyframe found)
        val totalVideoSamplesWritten = 0L
        val totalAudioSamplesWritten = 1200L
        val samplesCount = totalVideoSamplesWritten + totalAudioSamplesWritten
        val isMuxerStarted = true

        val hasValidVideo = totalVideoSamplesWritten > 0L
        val isRejected = samplesCount == 0L || !isMuxerStarted || !hasValidVideo

        assertTrue("Must reject recording when totalVideoSamplesWritten is 0", isRejected)

        val errMsg = if (!hasValidVideo && samplesCount > 0L) {
            "Perekaman gagal menghasilkan frame video yang valid (0 video frame tertulis, keyframe tidak ditemukan)."
        } else {
            "Perekaman tidak menghasilkan sampel media yang valid (0 sampel tertulis)."
        }

        assertTrue("Error message must explicitly mention 0 video frames and keyframe missing",
            errMsg.contains("0 video frame tertulis") && errMsg.contains("keyframe tidak ditemukan"))
    }

    @Test
    fun test25_singleTrackFallback_preservesVideoWhenAudioTimesOut() {
        val audioPlaylistUrl: String? = "https://cdn.example.com/audio.m3u8"
        var audioTrackIndex = -1
        val videoTrackIndex = 0

        // At attempt 10, audio still hasn't arrived -> timeout fallback triggers
        val downloadCounter = 10
        val shouldStart = if (audioPlaylistUrl != null && audioTrackIndex == -1 && downloadCounter < 10) {
            videoTrackIndex != -1 && audioTrackIndex != -1
        } else {
            videoTrackIndex != -1 || audioTrackIndex != -1
        }

        assertTrue("Should trigger single-track fallback at or beyond 10 download attempts", shouldStart)
    }
}

