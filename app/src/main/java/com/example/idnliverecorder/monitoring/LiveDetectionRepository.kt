package com.example.idnliverecorder.monitoring

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.data.DetectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.UUID

class LiveDetectionRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "idn_live_detection_repo"
        private const val KEY_DETECTIONS = "key_detections"
        private const val MAX_HISTORY_SIZE = 20

        @Volatile
        private var INSTANCE: LiveDetectionRepository? = null

        fun getInstance(context: Context): LiveDetectionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveDetectionRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _recentDetections = MutableStateFlow<List<DetectedLiveItem>>(loadDetections())
    val recentDetections: StateFlow<List<DetectedLiveItem>> = _recentDetections.asStateFlow()

    private val _activeConfirmation = MutableStateFlow<DetectedLiveItem?>(null)
    val activeConfirmation: StateFlow<DetectedLiveItem?> = _activeConfirmation.asStateFlow()

    private val _todayDetectionCount = MutableStateFlow(calculateTodayCount(_recentDetections.value))
    val todayDetectionCount: StateFlow<Int> = _todayDetectionCount.asStateFlow()

    // Key -> timestamp of processed notifications
    private val processedNotificationKeys = mutableMapOf<String, Long>()

    /**
     * Checks if notification should be debounced / cooldown.
     * Returns true if it is duplicate or within cooldown.
     */
    @Synchronized
    fun isDuplicateOrInCooldown(
        notificationKey: String,
        streamerName: String,
        cooldownMinutes: Int,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. Check exact notification key
        val keyTime = processedNotificationKeys[notificationKey]
        if (keyTime != null && (currentTimeMs - keyTime) < (cooldownMinutes * 60_000L)) {
            return true
        }

        // 2. Check if same streamer was detected recently within cooldown
        val cooldownMillis = cooldownMinutes * 60_000L
        val recentMatch = _recentDetections.value.firstOrNull { item ->
            item.streamerName.equals(streamerName, ignoreCase = true) &&
                    (currentTimeMs - item.detectedAtMs) < cooldownMillis
        }

        return recentMatch != null
    }

    @Synchronized
    fun addDetection(
        notificationKey: String,
        streamerName: String,
        content: String,
        packageName: String,
        matchedKeyword: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): DetectedLiveItem {
        processedNotificationKeys[notificationKey] = currentTimeMs

        // If there was an existing Waiting confirmation, mark it as Expired
        val currentList = _recentDetections.value.map { item ->
            if (item.status == DetectionStatus.Waiting) {
                item.copy(status = DetectionStatus.Expired)
            } else {
                item
            }
        }.toMutableList()

        val newItem = DetectedLiveItem(
            id = if (notificationKey.isNotBlank()) notificationKey else UUID.randomUUID().toString(),
            streamerName = streamerName,
            notificationContent = content,
            detectedAtMs = currentTimeMs,
            status = DetectionStatus.Waiting,
            packageName = packageName,
            matchedKeyword = matchedKeyword
        )

        currentList.add(0, newItem)
        val trimmedList = currentList.take(MAX_HISTORY_SIZE)

        _recentDetections.value = trimmedList
        _activeConfirmation.value = newItem
        _todayDetectionCount.value = calculateTodayCount(trimmedList)

        saveDetections(trimmedList)
        return newItem
    }

    @Synchronized
    fun updateDetectionStatus(id: String, newStatus: DetectionStatus) {
        val updated = _recentDetections.value.map { item ->
            if (item.id == id) {
                item.copy(status = newStatus)
            } else {
                item
            }
        }
        _recentDetections.value = updated

        if (_activeConfirmation.value?.id == id) {
            _activeConfirmation.value = if (newStatus == DetectionStatus.Waiting) {
                _activeConfirmation.value?.copy(status = newStatus)
            } else {
                null
            }
        }

        saveDetections(updated)
    }

    fun dismissActiveConfirmation() {
        _activeConfirmation.value?.let { active ->
            updateDetectionStatus(active.id, DetectionStatus.Ignored)
        }
        _activeConfirmation.value = null
    }

    fun setActiveConfirmation(item: DetectedLiveItem?) {
        _activeConfirmation.value = item
    }

    private fun calculateTodayCount(items: List<DetectedLiveItem>): Int {
        val calNow = Calendar.getInstance()
        val yearNow = calNow.get(Calendar.YEAR)
        val dayNow = calNow.get(Calendar.DAY_OF_YEAR)

        val calItem = Calendar.getInstance()
        return items.count { item ->
            calItem.timeInMillis = item.detectedAtMs
            calItem.get(Calendar.YEAR) == yearNow && calItem.get(Calendar.DAY_OF_YEAR) == dayNow
        }
    }

    private fun saveDetections(items: List<DetectedLiveItem>) {
        val entries = JSONArray()
        items.forEach { item ->
            entries.put(JSONObject().apply {
                put("id", item.id)
                put("streamerName", item.streamerName)
                put("notificationContent", item.notificationContent)
                put("detectedAtMs", item.detectedAtMs)
                put("status", item.status.name)
                put("packageName", item.packageName)
                put("matchedKeyword", item.matchedKeyword)
            })
        }
        val serialized = JSONObject().put("version", 1).put("items", entries).toString()
        prefs.edit().putString(KEY_DETECTIONS, serialized).apply()
    }

    private fun loadDetections(): List<DetectedLiveItem> {
        val raw = prefs.getString(KEY_DETECTIONS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        return parseJsonDetections(raw) ?: parseLegacyDetections(raw)
    }

    private fun parseJsonDetections(raw: String): List<DetectedLiveItem>? {
        return try {
            val entries = JSONObject(raw).optJSONArray("items") ?: return null
            buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val id = entry.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        DetectedLiveItem(
                            id = id,
                            streamerName = entry.optString("streamerName"),
                            notificationContent = entry.optString("notificationContent"),
                            detectedAtMs = entry.optLong("detectedAtMs", System.currentTimeMillis()),
                            status = runCatching {
                                DetectionStatus.valueOf(entry.optString("status"))
                            }.getOrDefault(DetectionStatus.Expired),
                            packageName = entry.optString("packageName"),
                            matchedKeyword = entry.optString("matchedKeyword")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Reads the old delimiter format so existing saved detection history survives the format change. */
    private fun parseLegacyDetections(raw: String): List<DetectedLiveItem> {
        return try {
            raw.split(";;").mapNotNull { entry ->
                val parts = entry.split("||")
                if (parts.size < 7) return@mapNotNull null
                DetectedLiveItem(
                    id = parts[0],
                    streamerName = parts[1],
                    notificationContent = parts[2],
                    detectedAtMs = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    status = runCatching { DetectionStatus.valueOf(parts[4]) }
                        .getOrDefault(DetectionStatus.Expired),
                    packageName = parts[5],
                    matchedKeyword = parts.drop(6).joinToString("||")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

