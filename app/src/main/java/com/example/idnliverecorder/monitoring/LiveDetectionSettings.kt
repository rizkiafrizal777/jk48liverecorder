package com.example.idnliverecorder.monitoring

import android.content.Context
import android.content.SharedPreferences
import com.example.idnliverecorder.core.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveDetectionSettingsData(
    val isEnabled: Boolean = true,
    val targetPackageName: String? = null,
    val targetAppName: String? = null,
    val keywords: List<String> = Constants.DEFAULT_LIVE_KEYWORDS,
    val cooldownMinutes: Int = Constants.DEFAULT_COOLDOWN_MINUTES,
    val notificationsEnabled: Boolean = true
)

class LiveDetectionSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "idn_live_detection_prefs"
        private const val KEY_ENABLED = "key_detection_enabled"
        private const val KEY_TARGET_PACKAGE = "key_target_package"
        private const val KEY_TARGET_APP_NAME = "key_target_app_name"
        private const val KEY_KEYWORDS = "key_keywords"
        private const val KEY_COOLDOWN_MINUTES = "key_cooldown_minutes"
        private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"

        @Volatile
        private var INSTANCE: LiveDetectionSettings? = null

        fun getInstance(context: Context): LiveDetectionSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveDetectionSettings(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<LiveDetectionSettingsData> = _settingsFlow.asStateFlow()

    private fun loadSettings(): LiveDetectionSettingsData {
        val isEnabled = prefs.getBoolean(KEY_ENABLED, true)
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, null)
        val targetAppName = prefs.getString(KEY_TARGET_APP_NAME, null)
        val keywordsStr = prefs.getString(KEY_KEYWORDS, null)
        val keywords = if (keywordsStr.isNullOrBlank()) {
            Constants.DEFAULT_LIVE_KEYWORDS
        } else {
            keywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val cooldownMinutes = prefs.getInt(KEY_COOLDOWN_MINUTES, Constants.DEFAULT_COOLDOWN_MINUTES)
        val notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

        return LiveDetectionSettingsData(
            isEnabled = isEnabled,
            targetPackageName = targetPackage,
            targetAppName = targetAppName,
            keywords = keywords,
            cooldownMinutes = cooldownMinutes,
            notificationsEnabled = notificationsEnabled
        )
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isEnabled = enabled)
    }

    fun setTargetApplication(packageName: String?, appName: String?) {
        prefs.edit()
            .putString(KEY_TARGET_PACKAGE, packageName)
            .putString(KEY_TARGET_APP_NAME, appName)
            .apply()
        _settingsFlow.value = _settingsFlow.value.copy(
            targetPackageName = packageName,
            targetAppName = appName
        )
    }

    fun setKeywords(keywords: List<String>) {
        val cleaned = keywords.map { it.trim() }.filter { it.isNotEmpty() }
        val finalKeywords = if (cleaned.isEmpty()) Constants.DEFAULT_LIVE_KEYWORDS else cleaned
        val storedStr = finalKeywords.joinToString(",")
        prefs.edit().putString(KEY_KEYWORDS, storedStr).apply()
        _settingsFlow.value = _settingsFlow.value.copy(keywords = finalKeywords)
    }

    fun setCooldownMinutes(minutes: Int) {
        val validMinutes = minutes.coerceIn(1, 120)
        prefs.edit().putInt(KEY_COOLDOWN_MINUTES, validMinutes).apply()
        _settingsFlow.value = _settingsFlow.value.copy(cooldownMinutes = validMinutes)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(notificationsEnabled = enabled)
    }
}

