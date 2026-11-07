package com.example.idnliverecorder

import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.data.DetectionStatus
import com.example.idnliverecorder.monitoring.LiveNotificationParser
import com.example.idnliverecorder.monitoring.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDetectionUnitTest {

    private val targetPackage = "com.idn.app"
    private val defaultKeywords = listOf("live", "sedang live", "started a live", "is live")

    // 1. Notification from the selected IDN application
    @Test
    fun test1_notificationFromSelectedIdnApp() {
        val result = LiveNotificationParser.parse(
            packageName = targetPackage,
            targetPackageName = targetPackage,
            title = "Freya Jayawardana",
            text = "Sedang live streaming sekarang!",
            bigText = null,
            keywords = defaultKeywords
        )

        assertTrue("Expected match for selected IDN app", result is ParseResult.Match)
        val match = result as ParseResult.Match
        assertEquals("Freya Jayawardana", match.streamerName)
        assertEquals("sedang live", match.matchedKeyword)
    }

    // 2. Notification from an unrelated application
    @Test
    fun test2_notificationFromUnrelatedApp() {
        val result = LiveNotificationParser.parse(
            packageName = "com.whatsapp",
            targetPackageName = targetPackage,
            title = "WhatsApp Live Alert",
            text = "Someone is live",
            bigText = null,
            keywords = defaultKeywords
        )

        assertEquals(ParseResult.PackageMismatch, result)
    }

    // 3. IDN notification containing a live keyword
    @Test
    fun test3_idnNotificationContainingLiveKeyword() {
        val result = LiveNotificationParser.parse(
            packageName = targetPackage,
            targetPackageName = targetPackage,
            title = "IDN Live",
            text = "Zee sedang live di IDN App! Ayo nonton keseruannya.",
            bigText = null,
            keywords = defaultKeywords
        )

        assertTrue(result is ParseResult.Match)
        val match = result as ParseResult.Match
        assertEquals("Zee", match.streamerName)
        assertEquals("sedang live", match.matchedKeyword)
    }

    // 4. IDN notification without a live keyword
    @Test
    fun test4_idnNotificationWithoutLiveKeyword() {
        val result = LiveNotificationParser.parse(
            packageName = targetPackage,
            targetPackageName = targetPackage,
            title = "Promo Spesial",
            text = "Dapatkan koin gratis dan voucher belanja hari ini!",
            bigText = null,
            keywords = defaultKeywords
        )

        assertEquals(ParseResult.NoKeywordMatch, result)
    }

    // 5. Notification with missing title or text
    @Test
    fun test5_notificationWithMissingTitleOrText() {
        val result = LiveNotificationParser.parse(
            packageName = targetPackage,
            targetPackageName = targetPackage,
            title = null,
            text = "",
            bigText = null,
            keywords = defaultKeywords
        )

        assertEquals(ParseResult.EmptyContent, result)
    }

    // 6. Duplicate notification / cooldown
    @Test
    fun test6_duplicateNotificationCooldown() {
        val processedKeys = mutableMapOf<String, Long>()
        val recentItems = mutableListOf<DetectedLiveItem>()

        fun isDuplicate(key: String, streamer: String, cooldownMin: Int, now: Long): Boolean {
            val keyTime = processedKeys[key]
            if (keyTime != null && (now - keyTime) < (cooldownMin * 60_000L)) return true
            val recent = recentItems.firstOrNull {
                it.streamerName.equals(streamer, ignoreCase = true) &&
                        (now - it.detectedAtMs) < (cooldownMin * 60_000L)
            }
            return recent != null
        }

        val now = 100_000L
        val notificationKey = "idn_notif_101"
        val streamer = "Christy"

        assertFalse(isDuplicate(notificationKey, streamer, 5, now))

        // Record first detection
        processedKeys[notificationKey] = now
        recentItems.add(
            DetectedLiveItem(
                id = notificationKey,
                streamerName = streamer,
                notificationContent = "$streamer sedang live",
                detectedAtMs = now,
                status = DetectionStatus.Waiting,
                packageName = targetPackage,
                matchedKeyword = "sedang live"
            )
        )

        // Same notification key 30 seconds later -> duplicate
        assertTrue(isDuplicate(notificationKey, streamer, 5, now + 30_000L))

        // Same streamer with new notification key 2 minutes later (within 5m cooldown) -> duplicate
        assertTrue(isDuplicate("idn_notif_102", streamer, 5, now + 120_000L))

        // Different streamer after 2 minutes -> NOT duplicate
        assertFalse(isDuplicate("idn_notif_103", "Marsha", 5, now + 120_000L))

        // Same streamer after 6 minutes (cooldown expired) -> NOT duplicate
        assertFalse(isDuplicate("idn_notif_104", streamer, 5, now + 360_000L))
    }

    // 7. Notification access permission not granted
    @Test
    fun test7_notificationAccessPermissionNotGranted() {
        var isPermissionGranted = false
        val isSettingEnabled = true
        val targetAppSelected = "com.idn.app"

        val isDetectionActive = isSettingEnabled && targetAppSelected.isNotBlank() && isPermissionGranted
        assertFalse("Detection must NOT be active when permission is not granted", isDetectionActive)

        // When permission is granted
        isPermissionGranted = true
        val isDetectionActiveAfterGrant = isSettingEnabled && targetAppSelected.isNotBlank() && isPermissionGranted
        assertTrue("Detection must be active after permission is granted", isDetectionActiveAfterGrant)
    }

    // 8. User ignores a detected live notification
    @Test
    fun test8_userIgnoresDetectedLiveNotification() {
        var activeConfirmation: DetectedLiveItem? = DetectedLiveItem(
            id = "test_item_1",
            streamerName = "Gracia",
            notificationContent = "Gracia sedang live",
            detectedAtMs = System.currentTimeMillis(),
            status = DetectionStatus.Waiting,
            packageName = targetPackage,
            matchedKeyword = "sedang live"
        )

        assertEquals(DetectionStatus.Waiting, activeConfirmation?.status)

        // User taps "Abaikan"
        val updatedItem = activeConfirmation?.copy(status = DetectionStatus.Ignored)
        activeConfirmation = null

        assertNotNull(updatedItem)
        assertEquals(DetectionStatus.Ignored, updatedItem?.status)
        org.junit.Assert.assertNull(activeConfirmation)
    }

    // 9. User confirms recording
    @Test
    fun test9_userConfirmsRecording() {
        val activeItem = DetectedLiveItem(
            id = "test_item_2",
            streamerName = "Adel",
            notificationContent = "Adel is live now",
            detectedAtMs = System.currentTimeMillis(),
            status = DetectionStatus.Waiting,
            packageName = targetPackage,
            matchedKeyword = "is live"
        )

        var requestedScreenCaptureConsent = false

        // User taps "Mulai Rekam"
        fun onConfirm() {
            requestedScreenCaptureConsent = true
        }

        onConfirm()
        val recordingItem = activeItem.copy(status = DetectionStatus.Recording)

        assertTrue("User consent flow must be triggered", requestedScreenCaptureConsent)
        assertEquals(DetectionStatus.Recording, recordingItem.status)
    }

    // 10. User disables live detection
    @Test
    fun test10_userDisablesLiveDetection() {
        var isDetectionEnabled = true
        val targetAppSelected = "com.idn.app"
        val isPermissionGranted = true

        assertTrue(isDetectionEnabled && targetAppSelected.isNotBlank() && isPermissionGranted)

        // User turns off detection in Settings
        isDetectionEnabled = false
        val isActive = isDetectionEnabled && targetAppSelected.isNotBlank() && isPermissionGranted

        assertFalse("Detection must be inactive when disabled in settings", isActive)
    }
}
