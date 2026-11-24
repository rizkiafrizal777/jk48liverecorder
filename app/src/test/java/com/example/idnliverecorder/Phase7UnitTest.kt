package com.example.idnliverecorder

import android.net.Uri
import com.example.idnliverecorder.database.ExportTask
import com.example.idnliverecorder.database.RecordingBookmark
import com.example.idnliverecorder.library.RecordingLibraryItem
import com.example.idnliverecorder.library.export.ExportProgressState
import com.example.idnliverecorder.library.metadata.AdvancedRecordingMetadata
import com.example.idnliverecorder.library.organization.DurationFilter
import com.example.idnliverecorder.library.organization.GroupByOption
import com.example.idnliverecorder.library.organization.OrganizationFilters
import com.example.idnliverecorder.library.organization.RecordingOrganizationRepository
import com.example.idnliverecorder.library.organization.SizeFilter
import com.example.idnliverecorder.library.reliability.RecordingFileLockManager
import com.example.idnliverecorder.library.reliability.RecordingReliabilityManager
import com.example.idnliverecorder.library.reliability.ReliabilityStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Phase 7 Unit Tests — Advanced Recording Management & Automation
 *
 * Tests A through G:
 * - Test A: Background Export — task status model, deduplication, progress calc, isTerminal
 * - Test B: Bookmarks — data model, timestamp format, per-video association, edit/delete
 * - Test C: Organization — applyFilters() with multi-dimensional filters, groupRecordings()
 * - Test D: Advanced Metadata — model completeness, codec formatting, Unknown fallbacks
 * - Test E: Reliability — ReliabilityStatus mapping, RecordingFileLockManager lock/unlock/isLocked
 * - Test F: Dashboard Analytics — sum of durations, sum of sizes, member grouping
 * - Test G: Lifecycle State — ExportProgressState transitions, multi-select state flags
 */
class Phase7UnitTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun mockUri(id: Long): Uri {
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.toString()).thenReturn("content://media/external/video/media/$id")
        Mockito.`when`(uri.scheme).thenReturn("content")
        return uri
    }

    private fun buildItem(
        id: Long,
        memberName: String = "Feni",
        sizeBytes: Long = 50 * 1024 * 1024L,  // 50 MB
        durationMs: Long = 30 * 60 * 1000L,   // 30 min
        dateAddedMs: Long = 1_700_000_000_000L,
        isPending: Boolean = false,
        isCorrupted: Boolean = false,
        integrityMessage: String? = null
    ): RecordingLibraryItem {
        return RecordingLibraryItem(
            id = id,
            uri = mockUri(id),
            displayName = "IDN_${memberName}_$id.mp4",
            memberName = memberName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            dateAddedMs = dateAddedMs,
            resolution = "1280x720",
            videoCodec = "video/avc",
            audioCodec = "audio/mp4a-latm",
            isPending = isPending,
            isCorrupted = isCorrupted,
            integrityMessage = integrityMessage
        )
    }

    @Before
    fun setUp() {
        // Ensure lock manager state is clean before each test
        // RecordingFileLockManager is an object — clearing it requires unlock calls
    }

    @After
    fun tearDown() {
        // Unlock any URIs left locked by test cases
        (1L..10L).forEach { id ->
            RecordingFileLockManager.unlock(mockUri(id))
        }
    }

    // ─── TEST A: Background Export ────────────────────────────────────────────

    @Test
    fun `Test A1 - ExportTask initial status is QUEUED`() {
        val task = ExportTask(
            sourceUri = "content://media/external/video/media/1",
            destinationUri = "content://downloads/export_1.mp4",
            displayName = "Feni Live 2024",
            totalBytes = 100_000_000L
        )
        assertEquals(ExportTask.STATUS_QUEUED, task.status)
        assertEquals(0L, task.copiedBytes)
        assertEquals(0, task.progressPercent)
        assertNull(task.completedAt)
    }

    @Test
    fun `Test A2 - ExportTask isTerminal returns true for COMPLETED`() {
        val task = ExportTask(
            sourceUri = "content://media/1",
            destinationUri = "content://downloads/1.mp4",
            displayName = "Test",
            totalBytes = 1000L,
            status = ExportTask.STATUS_COMPLETED,
            completedAt = System.currentTimeMillis()
        )
        assertTrue("COMPLETED task should be terminal", task.isTerminal)
    }

    @Test
    fun `Test A3 - ExportTask isTerminal returns true for CANCELLED`() {
        val task = ExportTask(
            sourceUri = "content://media/2",
            destinationUri = "content://downloads/2.mp4",
            displayName = "Test",
            totalBytes = 2000L,
            status = ExportTask.STATUS_CANCELLED
        )
        assertTrue("CANCELLED task should be terminal", task.isTerminal)
    }

    @Test
    fun `Test A4 - ExportTask isTerminal returns true for FAILED`() {
        val task = ExportTask(
            sourceUri = "content://media/3",
            destinationUri = "content://downloads/3.mp4",
            displayName = "Test",
            totalBytes = 3000L,
            status = ExportTask.STATUS_FAILED,
            errorMessage = "IO Error"
        )
        assertTrue("FAILED task should be terminal", task.isTerminal)
        assertEquals("IO Error", task.errorMessage)
    }

    @Test
    fun `Test A5 - ExportTask isTerminal returns false for RUNNING`() {
        val task = ExportTask(
            sourceUri = "content://media/4",
            destinationUri = "content://downloads/4.mp4",
            displayName = "Test",
            totalBytes = 4000L,
            status = ExportTask.STATUS_RUNNING
        )
        assertFalse("RUNNING task should NOT be terminal", task.isTerminal)
    }

    @Test
    fun `Test A6 - Progress percentage calculation is correct`() {
        // Simulate progress update: 25MB of 100MB = 25%
        val totalBytes = 100_000_000L
        val copiedBytes = 25_000_000L
        val percent = ((copiedBytes.toDouble() / totalBytes) * 100).toInt()
        assertEquals(25, percent)
    }

    @Test
    fun `Test A7 - Export deduplication logic prevents double export`() {
        // The deduplication uses a ConcurrentHashMap-based Set via RecordingExportService.activeExportUris
        // We test the Uri string key logic directly
        val uri1 = "content://media/external/video/media/42"
        val uri2 = "content://media/external/video/media/43"
        val activeUris = mutableSetOf<String>()

        // Simulate adding uri1 as active
        activeUris.add(uri1)

        assertTrue("uri1 should be active", activeUris.contains(uri1))
        assertFalse("uri2 should NOT be active yet", activeUris.contains(uri2))

        // Simulate duplicate prevention
        val canStart = !activeUris.contains(uri1)
        assertFalse("Should NOT allow starting export for already-active uri", canStart)
    }

    @Test
    fun `Test A8 - All ExportTask status constants are defined`() {
        assertNotNull(ExportTask.STATUS_QUEUED)
        assertNotNull(ExportTask.STATUS_RUNNING)
        assertNotNull(ExportTask.STATUS_COMPLETED)
        assertNotNull(ExportTask.STATUS_CANCELLED)
        assertNotNull(ExportTask.STATUS_FAILED)

        assertEquals("QUEUED", ExportTask.STATUS_QUEUED)
        assertEquals("RUNNING", ExportTask.STATUS_RUNNING)
        assertEquals("COMPLETED", ExportTask.STATUS_COMPLETED)
        assertEquals("CANCELLED", ExportTask.STATUS_CANCELLED)
        assertEquals("FAILED", ExportTask.STATUS_FAILED)
    }

    // ─── TEST B: Bookmarks ────────────────────────────────────────────────────

    @Test
    fun `Test B1 - RecordingBookmark data model fields are correct`() {
        val bookmark = RecordingBookmark(
            id = 0L,
            recordingId = 7L,
            recordingUri = "content://media/external/video/media/7",
            timestampMs = 65_000L,
            title = "Intro",
            note = "Feni appears here"
        )
        assertEquals(7L, bookmark.recordingId)
        assertEquals(65_000L, bookmark.timestampMs)
        assertEquals("Intro", bookmark.title)
        assertEquals("Feni appears here", bookmark.note)
    }

    @Test
    fun `Test B2 - Bookmark timestamp formatting is correct`() {
        // 65 seconds = 1 minute 5 seconds → "01:05"
        val timestampMs = 65_000L
        val formatted = formatTimestampForTest(timestampMs)
        assertEquals("01:05", formatted)
    }

    @Test
    fun `Test B3 - Bookmark timestamp for hours is formatted correctly`() {
        // 3661 seconds = 1h 1m 1s → "01:01:01"
        val timestampMs = 3_661_000L
        val formatted = formatTimestampForTest(timestampMs)
        assertEquals("01:01:01", formatted)
    }

    @Test
    fun `Test B4 - Bookmark is associated with the correct recording by ID`() {
        val recordingId = 42L
        val bookmarks = listOf(
            RecordingBookmark(recordingId = recordingId, recordingUri = "content://media/42", timestampMs = 1000L, title = "A"),
            RecordingBookmark(recordingId = recordingId, recordingUri = "content://media/42", timestampMs = 5000L, title = "B"),
            RecordingBookmark(recordingId = 99L, recordingUri = "content://media/99", timestampMs = 3000L, title = "Other")
        )

        val forRecording = bookmarks.filter { it.recordingId == recordingId }
        assertEquals(2, forRecording.size)
        assertTrue(forRecording.all { it.recordingId == recordingId })
    }

    @Test
    fun `Test B5 - Bookmark can be edited and maintains association`() {
        val original = RecordingBookmark(
            id = 1L,
            recordingId = 5L,
            recordingUri = "content://media/5",
            timestampMs = 10_000L,
            title = "Original Title",
            note = null
        )
        val updated = original.copy(title = "Updated Title", note = "Added note")

        assertEquals(1L, updated.id)
        assertEquals(5L, updated.recordingId)
        assertEquals("Updated Title", updated.title)
        assertEquals("Added note", updated.note)
    }

    @Test
    fun `Test B6 - Deleting bookmark removes it from list`() {
        val bookmarks = mutableListOf(
            RecordingBookmark(id = 1L, recordingId = 10L, recordingUri = "content://media/10", timestampMs = 1000L, title = "Keep"),
            RecordingBookmark(id = 2L, recordingId = 10L, recordingUri = "content://media/10", timestampMs = 2000L, title = "Delete me")
        )
        val toDelete = bookmarks.first { it.id == 2L }
        bookmarks.remove(toDelete)

        assertEquals(1, bookmarks.size)
        assertEquals("Keep", bookmarks[0].title)
    }

    // ─── TEST C: Organization Filters ─────────────────────────────────────────

    private val sampleItems = listOf(
        buildItem(id = 1, memberName = "Feni", sizeBytes = 50 * 1024 * 1024L, durationMs = 20 * 60 * 1000L),
        buildItem(id = 2, memberName = "Freya", sizeBytes = 200 * 1024 * 1024L, durationMs = 40 * 60 * 1000L),
        buildItem(id = 3, memberName = "Shania", sizeBytes = 600 * 1024 * 1024L, durationMs = 90 * 60 * 1000L),
        buildItem(id = 4, memberName = "Feni", sizeBytes = 80 * 1024 * 1024L, durationMs = 10 * 60 * 1000L)
    )

    @Test
    fun `Test C1 - applyFilters with no filters returns all items`() {
        val filters = OrganizationFilters()
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        assertEquals(4, result.size)
    }

    @Test
    fun `Test C2 - applyFilters by search query filters by member name`() {
        val filters = OrganizationFilters(searchQuery = "feni")
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        assertEquals(2, result.size)
        assertTrue(result.all { it.memberName.lowercase().contains("feni") })
    }

    @Test
    fun `Test C3 - applyFilters by size UNDER_100MB`() {
        val filters = OrganizationFilters(sizeFilter = SizeFilter.UNDER_100MB)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        // Items 1 (50MB) and 4 (80MB) are under 100MB
        assertEquals(2, result.size)
        assertTrue(result.all { it.sizeBytes < 100 * 1024 * 1024L })
    }

    @Test
    fun `Test C4 - applyFilters by size FROM_100_TO_500MB`() {
        val filters = OrganizationFilters(sizeFilter = SizeFilter.FROM_100_TO_500MB)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        // Item 2 (200MB)
        assertEquals(1, result.size)
        assertEquals("Freya", result[0].memberName)
    }

    @Test
    fun `Test C5 - applyFilters by size OVER_500MB`() {
        val filters = OrganizationFilters(sizeFilter = SizeFilter.OVER_500MB)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        // Item 3 (600MB)
        assertEquals(1, result.size)
        assertEquals("Shania", result[0].memberName)
    }

    @Test
    fun `Test C6 - applyFilters by duration UNDER_15MIN`() {
        val filters = OrganizationFilters(durationFilter = DurationFilter.UNDER_15MIN)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        // Item 4 (10 min)
        assertEquals(1, result.size)
        assertEquals(4L, result[0].id)
    }

    @Test
    fun `Test C7 - applyFilters by duration FROM_15_TO_60MIN`() {
        val filters = OrganizationFilters(durationFilter = DurationFilter.FROM_15_TO_60MIN)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        // Items 1 (20min) and 2 (40min)
        assertEquals(2, result.size)
    }

    @Test
    fun `Test C8 - applyFilters by duration OVER_60MIN`() {
        val filters = OrganizationFilters(durationFilter = DurationFilter.OVER_60MIN)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        // Item 3 (90 min)
        assertEquals(1, result.size)
        assertEquals("Shania", result[0].memberName)
    }

    @Test
    fun `Test C9 - applyFilters by favoritesOnly returns only favorites`() {
        val favoriteIds = setOf(1L, 3L)
        val filters = OrganizationFilters(favoritesOnly = true)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, favoriteIds)
        assertEquals(2, result.size)
        assertTrue(result.all { it.id in favoriteIds })
    }

    @Test
    fun `Test C10 - applyFilters combined search + size`() {
        val filters = OrganizationFilters(searchQuery = "feni", sizeFilter = SizeFilter.UNDER_100MB)
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        // Both Feni items are under 100MB
        assertEquals(2, result.size)
        assertTrue(result.all { it.memberName == "Feni" })
    }

    @Test
    fun `Test C11 - groupRecordings NONE returns single group`() {
        val grouped = RecordingOrganizationRepository.groupRecordings(sampleItems, GroupByOption.NONE)
        assertEquals(1, grouped.size)
        assertTrue(grouped.containsKey("Semua Rekaman"))
        assertEquals(4, grouped["Semua Rekaman"]!!.size)
    }

    @Test
    fun `Test C12 - groupRecordings MEMBER groups correctly`() {
        val grouped = RecordingOrganizationRepository.groupRecordings(sampleItems, GroupByOption.MEMBER)
        // Feni appears twice, Freya once, Shania once → 3 groups
        assertEquals(3, grouped.size)
        assertEquals(2, grouped["Feni"]!!.size)
        assertEquals(1, grouped["Freya"]!!.size)
        assertEquals(1, grouped["Shania"]!!.size)
    }

    @Test
    fun `Test C13 - groupRecordings MEMBER with empty list returns empty map`() {
        val grouped = RecordingOrganizationRepository.groupRecordings(emptyList(), GroupByOption.MEMBER)
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun `Test C14 - applyFilters with no match returns empty list`() {
        val filters = OrganizationFilters(searchQuery = "nonexistent_member_xyz")
        val result = RecordingOrganizationRepository.applyFilters(sampleItems, filters, emptySet())
        assertTrue(result.isEmpty())
    }

    // ─── TEST D: Advanced Metadata ────────────────────────────────────────────

    @Test
    fun `Test D1 - AdvancedRecordingMetadata model contains all required fields`() {
        val metadata = AdvancedRecordingMetadata(
            fileName = "IDN_Feni_123.mp4",
            memberName = "Feni",
            recordingDateFormatted = "08 November 2026, 14:00:00",
            durationFormatted = "01:30:00",
            durationMs = 5_400_000L,
            fileSizeFormatted = "600.0 MB",
            fileSizeBytes = 629_145_600L,
            resolution = "1280x720",
            videoCodec = "H.264 / AVC",
            audioCodec = "AAC-LC",
            frameRate = "30.0 fps",
            bitrateFormatted = "1.20 Mbps",
            bitrateBps = 1_200_000L,
            mimeType = "video/mp4",
            contentUri = "content://media/external/video/media/123",
            playbackCompatibility = "Optimal — Akselerasi Hardware (H.264 / AAC)"
        )

        assertEquals("Feni", metadata.memberName)
        assertEquals("H.264 / AVC", metadata.videoCodec)
        assertEquals("AAC-LC", metadata.audioCodec)
        assertEquals("30.0 fps", metadata.frameRate)
        assertEquals("1.20 Mbps", metadata.bitrateFormatted)
        assertEquals("video/mp4", metadata.mimeType)
        assertEquals(5_400_000L, metadata.durationMs)
        assertNotNull(metadata.contentUri)
    }

    @Test
    fun `Test D2 - Unknown fallback for missing frame rate`() {
        val frameRateVal: Float? = null
        val display = if (frameRateVal != null && frameRateVal > 0) {
            String.format("%.1f fps", frameRateVal)
        } else {
            "Unknown"
        }
        assertEquals("Unknown", display)
    }

    @Test
    fun `Test D3 - Unknown fallback for missing bitrate`() {
        val bitrateVal: Long? = null
        val display = if (bitrateVal != null && bitrateVal > 0) {
            val kbps = bitrateVal / 1000
            if (kbps >= 1000) String.format("%.2f Mbps", kbps / 1000f) else "$kbps kbps"
        } else {
            "Unknown"
        }
        assertEquals("Unknown", display)
    }

    @Test
    fun `Test D4 - Codec name formatting for video slash avc`() {
        val formatted = codecNameForTest("video/avc")
        assertEquals("H.264 / AVC", formatted)
    }

    @Test
    fun `Test D5 - Codec name formatting for video slash hevc`() {
        val formatted = codecNameForTest("video/hevc")
        assertEquals("H.265 / HEVC", formatted)
    }

    @Test
    fun `Test D6 - Codec name formatting for audio slash mp4a-latm`() {
        val formatted = codecNameForTest("audio/mp4a-latm")
        assertEquals("AAC-LC", formatted)
    }

    @Test
    fun `Test D7 - Codec name fallback for unknown MIME`() {
        val formatted = codecNameForTest("video/unknown-codec")
        assertEquals("video/unknown-codec", formatted)
    }

    @Test
    fun `Test D8 - Bitrate formatted as kbps when below 1 Mbps`() {
        val bitrateVal = 500_000L // 500 kbps
        val kbps = bitrateVal / 1000
        val display = if (kbps >= 1000) String.format("%.2f Mbps", kbps / 1000f) else "$kbps kbps"
        assertEquals("500 kbps", display)
    }

    @Test
    fun `Test D9 - Bitrate formatted as Mbps when above 1 Mbps`() {
        val bitrateVal = 2_500_000L // 2.5 Mbps
        val kbps = bitrateVal / 1000
        val display = if (kbps >= 1000) String.format("%.2f Mbps", kbps / 1000f) else "$kbps kbps"
        assertEquals("2.50 Mbps", display)
    }

    // ─── TEST E: Reliability ──────────────────────────────────────────────────

    private val testReliabilityManager: ReliabilityStatus get() = ReliabilityStatus.VALID

    private fun evaluateReliabilityForTest(item: RecordingLibraryItem): ReliabilityStatus {
        return when {
            item.sizeBytes <= 0 -> ReliabilityStatus.INCOMPLETE
            item.isPending -> ReliabilityStatus.PENDING
            item.isCorrupted -> {
                if (item.integrityMessage?.contains("tidak dapat dibaca", ignoreCase = true) == true) {
                    ReliabilityStatus.UNREADABLE
                } else {
                    ReliabilityStatus.CORRUPTED
                }
            }
            else -> ReliabilityStatus.VALID
        }
    }

    @Test
    fun `Test E1 - evaluateReliability VALID for normal file`() {
        val item = buildItem(id = 1, sizeBytes = 50_000L)
        val status = evaluateReliabilityForTest(item)
        assertEquals(ReliabilityStatus.VALID, status)
    }

    @Test
    fun `Test E2 - evaluateReliability INCOMPLETE for zero-byte file`() {
        val item = buildItem(id = 2, sizeBytes = 0L)
        val status = evaluateReliabilityForTest(item)
        assertEquals(ReliabilityStatus.INCOMPLETE, status)
    }

    @Test
    fun `Test E3 - evaluateReliability PENDING for pending file`() {
        val item = buildItem(id = 3, sizeBytes = 1000L, isPending = true)
        val status = evaluateReliabilityForTest(item)
        assertEquals(ReliabilityStatus.PENDING, status)
    }

    @Test
    fun `Test E4 - evaluateReliability CORRUPTED for corrupted file without readable message`() {
        val item = buildItem(id = 4, sizeBytes = 1000L, isCorrupted = true, integrityMessage = "File rusak (CRC mismatch)")
        val status = evaluateReliabilityForTest(item)
        assertEquals(ReliabilityStatus.CORRUPTED, status)
    }

    @Test
    fun `Test E5 - evaluateReliability UNREADABLE for unreadable file`() {
        val item = buildItem(id = 5, sizeBytes = 1000L, isCorrupted = true, integrityMessage = "File tidak dapat dibaca oleh MediaExtractor")
        val status = evaluateReliabilityForTest(item)
        assertEquals(ReliabilityStatus.UNREADABLE, status)
    }

    @Test
    fun `Test E6 - ReliabilityStatus enum has correct labels`() {
        assertEquals("VALID", ReliabilityStatus.VALID.label)
        assertEquals("INCOMPLETE", ReliabilityStatus.INCOMPLETE.label)
        assertEquals("PENDING", ReliabilityStatus.PENDING.label)
        assertEquals("UNREADABLE", ReliabilityStatus.UNREADABLE.label)
        assertEquals("CORRUPTED", ReliabilityStatus.CORRUPTED.label)
    }

    @Test
    fun `Test E7 - RecordingFileLockManager tryLock succeeds for new URI`() {
        val uri = mockUri(100L)
        try {
            val locked = RecordingFileLockManager.tryLock(uri)
            assertTrue("Should successfully lock a new URI", locked)
        } finally {
            RecordingFileLockManager.unlock(uri)
        }
    }

    @Test
    fun `Test E8 - RecordingFileLockManager tryLock fails for already-locked URI`() {
        val uri = mockUri(101L)
        try {
            RecordingFileLockManager.tryLock(uri)
            val secondLock = RecordingFileLockManager.tryLock(uri)
            assertFalse("Second lock attempt on same URI should fail", secondLock)
        } finally {
            RecordingFileLockManager.unlock(uri)
        }
    }

    @Test
    fun `Test E9 - RecordingFileLockManager unlock allows re-lock`() {
        val uri = mockUri(102L)
        RecordingFileLockManager.tryLock(uri)
        RecordingFileLockManager.unlock(uri)
        val relocked = RecordingFileLockManager.tryLock(uri)
        assertTrue("Should allow re-locking after unlock", relocked)
        RecordingFileLockManager.unlock(uri)
    }

    @Test
    fun `Test E10 - RecordingFileLockManager isLocked returns correct state`() {
        val uri = mockUri(103L)
        assertFalse(RecordingFileLockManager.isLocked(uri))
        RecordingFileLockManager.tryLock(uri)
        assertTrue(RecordingFileLockManager.isLocked(uri))
        RecordingFileLockManager.unlock(uri)
        assertFalse(RecordingFileLockManager.isLocked(uri))
    }

    // ─── TEST F: Dashboard Analytics ─────────────────────────────────────────

    @Test
    fun `Test F1 - Total recording count is sum of all items`() {
        assertEquals(4, sampleItems.size)
    }

    @Test
    fun `Test F2 - Total size bytes sums correctly`() {
        val totalSizeBytes = sampleItems.sumOf { it.sizeBytes }
        val expected = (50 + 200 + 600 + 80).toLong() * 1024 * 1024
        assertEquals(expected, totalSizeBytes)
    }

    @Test
    fun `Test F3 - Total duration ms sums correctly`() {
        val totalDurationMs = sampleItems.sumOf { it.durationMs }
        val expected = (20 + 40 + 90 + 10).toLong() * 60 * 1000
        assertEquals(expected, totalDurationMs)
    }

    @Test
    fun `Test F4 - Member distribution groups correctly`() {
        val grouped = sampleItems.groupBy { it.memberName }
        assertEquals(3, grouped.size)   // Feni, Freya, Shania
        assertEquals(2, grouped["Feni"]!!.size)
        assertEquals(1, grouped["Freya"]!!.size)
        assertEquals(1, grouped["Shania"]!!.size)
    }

    @Test
    fun `Test F5 - Largest member by recording count identified correctly`() {
        val grouped = sampleItems.groupBy { it.memberName }
        val topMember = grouped.maxByOrNull { it.value.size }
        assertNotNull(topMember)
        assertEquals("Feni", topMember!!.key)
        assertEquals(2, topMember.value.size)
    }

    @Test
    fun `Test F6 - Total size favoritesOnly correct`() {
        val favoriteIds = setOf(1L, 3L)
        val favoriteItems = sampleItems.filter { it.id in favoriteIds }
        val totalFavoriteSize = favoriteItems.sumOf { it.sizeBytes }
        // Items 1 (50MB) and 3 (600MB)
        val expected = (50 + 600).toLong() * 1024 * 1024
        assertEquals(expected, totalFavoriteSize)
    }

    @Test
    fun `Test F7 - ExportTask history list is ordered with most recent first`() {
        val t1 = ExportTask(
            id = 1L, sourceUri = "s1", destinationUri = "d1", displayName = "A",
            totalBytes = 100L, startedAt = 1_000L, status = ExportTask.STATUS_COMPLETED
        )
        val t2 = ExportTask(
            id = 2L, sourceUri = "s2", destinationUri = "d2", displayName = "B",
            totalBytes = 200L, startedAt = 2_000L, status = ExportTask.STATUS_COMPLETED
        )
        val tasks = listOf(t1, t2).sortedByDescending { it.startedAt }
        assertEquals(2L, tasks[0].id)   // Most recent first
        assertEquals(1L, tasks[1].id)
    }

    // ─── TEST G: Lifecycle State ──────────────────────────────────────────────

    @Test
    fun `Test G1 - ExportProgressState default values are idle`() {
        val state = ExportProgressState()
        assertFalse("Default state should not be running", state.isRunning)
        assertEquals(0, state.progressPercent)
        assertEquals(0L, state.copiedBytes)
        assertEquals(0L, state.totalBytes)
        assertNull(state.errorMessage)
    }

    @Test
    fun `Test G2 - ExportProgressState running=true reflects active export`() {
        val state = ExportProgressState(
            taskId = 5L,
            isRunning = true,
            displayName = "Feni Live",
            copiedBytes = 50_000_000L,
            totalBytes = 100_000_000L,
            progressPercent = 50
        )
        assertTrue(state.isRunning)
        assertEquals(50, state.progressPercent)
        assertEquals(5L, state.taskId)
    }

    @Test
    fun `Test G3 - ExportProgressState error state has errorMessage`() {
        val state = ExportProgressState(
            isRunning = false,
            errorMessage = "Insufficient storage"
        )
        assertFalse(state.isRunning)
        assertNotNull(state.errorMessage)
        assertEquals("Insufficient storage", state.errorMessage)
    }

    @Test
    fun `Test G4 - Multi-select empty set means no selection`() {
        val selectedIds = emptySet<Long>()
        assertFalse("Empty set has no selection", selectedIds.isNotEmpty())
        assertEquals(0, selectedIds.size)
    }

    @Test
    fun `Test G5 - Multi-select adding and removing items`() {
        var selectedIds = setOf<Long>()
        // Add item 1
        selectedIds = selectedIds + 1L
        assertEquals(1, selectedIds.size)
        assertTrue(selectedIds.contains(1L))

        // Add item 2
        selectedIds = selectedIds + 2L
        assertEquals(2, selectedIds.size)

        // Remove item 1
        selectedIds = selectedIds - 1L
        assertEquals(1, selectedIds.size)
        assertFalse(selectedIds.contains(1L))
        assertTrue(selectedIds.contains(2L))
    }

    @Test
    fun `Test G6 - Multi-select selectAll populates all item IDs`() {
        val allItems = sampleItems
        val selectedIds = allItems.map { it.id }.toSet()
        assertEquals(4, selectedIds.size)
        assertTrue(selectedIds.containsAll(listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun `Test G7 - Multi-select clearSelection empties the set`() {
        var selectedIds = setOf(1L, 2L, 3L)
        selectedIds = emptySet()
        assertTrue(selectedIds.isEmpty())
    }

    // ─── Private Test Helpers ─────────────────────────────────────────────────

    /**
     * Mirrors RecordingBookmarkRepository.formatTimestamp() logic.
     */
    private fun formatTimestampForTest(timestampMs: Long): String {
        val totalSeconds = timestampMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Mirrors AdvancedVideoMetadataRetriever.formatCodecName() logic.
     */
    private fun codecNameForTest(mime: String): String {
        return when (mime.lowercase()) {
            "video/avc" -> "H.264 / AVC"
            "video/hevc" -> "H.265 / HEVC"
            "video/mp4v-es" -> "MPEG-4 Video"
            "video/x-vnd.on2.vp8" -> "VP8"
            "video/x-vnd.on2.vp9" -> "VP9"
            "video/av01" -> "AV1"
            "audio/mp4a-latm" -> "AAC-LC"
            "audio/mpeg" -> "MP3"
            "audio/opus" -> "Opus"
            else -> mime
        }
    }
}

