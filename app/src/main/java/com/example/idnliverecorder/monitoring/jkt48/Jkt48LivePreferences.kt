package com.example.idnliverecorder.monitoring.jkt48

import android.content.Context
import android.content.SharedPreferences
import com.example.idnliverecorder.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages configuration for JKT48 Live Monitor.
 *
 * Security Notice:
 * Keys are stored locally on device and are never sent to external third parties
 * or logged to Logcat. Prototype warning: Keys stored on client devices can be
 * extracted through APK decompilation; do not use production keys in distributed APKs.
 */
class Jkt48LivePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "jkt48_live_monitor_prefs"
        private const val KEY_API_KEY = "jkt48_api_key"
        private const val KEY_BASE_URL = "jkt48_base_url"
        private const val KEY_NOTIFY_FAVORITES = "notify_favorites_live"
        private const val KEY_NOTIFIED_SESSIONS = "notified_live_session_ids"
        private const val MAX_STORED_SESSIONS = 50
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(loadApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _baseUrl = MutableStateFlow(loadBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _notifyFavorites = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_FAVORITES, true))
    val notifyFavorites: StateFlow<Boolean> = _notifyFavorites.asStateFlow()

    private fun loadApiKey(): String {
        val stored = prefs.getString(KEY_API_KEY, null)
        return if (!stored.isNullOrBlank()) {
            stored
        } else {
            // Fallback to BuildConfig if provided in local.properties
            BuildConfig.JKT48_CONNECT_API_KEY.ifBlank { "" }
        }
    }

    private fun loadBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, null) ?: "https://v2.jkt48connect.com/"
    }

    fun setApiKey(newKey: String) {
        val trimmed = newKey.trim()
        prefs.edit().putString(KEY_API_KEY, trimmed).apply()
        _apiKey.value = trimmed
    }

    fun setBaseUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        prefs.edit().putString(KEY_BASE_URL, trimmed).apply()
        _baseUrl.value = trimmed
    }

    fun setNotifyFavorites(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_FAVORITES, enabled).apply()
        _notifyFavorites.value = enabled
    }

    /**
     * Checks if this session has already triggered a notification.
     */
    fun hasNotifiedSession(sessionKey: String): Boolean {
        val notified = prefs.getStringSet(KEY_NOTIFIED_SESSIONS, emptySet()) ?: emptySet()
        return notified.contains(sessionKey)
    }

    /**
     * Records that a notification was sent for this live session to avoid duplicate alerts.
     */
    fun markSessionNotified(sessionKey: String) {
        val current = prefs.getStringSet(KEY_NOTIFIED_SESSIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.size >= MAX_STORED_SESSIONS) {
            current.clear() // Clear old history periodically to keep memory bounded
        }
        current.add(sessionKey)
        prefs.edit().putStringSet(KEY_NOTIFIED_SESSIONS, current).apply()
    }
}

