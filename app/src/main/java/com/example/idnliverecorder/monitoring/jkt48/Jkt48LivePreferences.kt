package com.example.idnliverecorder.monitoring.jkt48

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48ConnectApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages configuration for JKT48 Live Monitor.
 *
 * Security Notice:
 * Keys are encrypted at rest using an Android Keystore AES-GCM key. The API key is
 * still present in app memory and in the HTTPS request, so client-side storage cannot
 * make a credential safe to embed as a shared production secret in a distributed APK.
 */
class Jkt48LivePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "jkt48_live_monitor_prefs"
        private const val KEY_API_KEY = "jkt48_api_key"
        private const val KEY_API_KEY_ENCRYPTED = "jkt48_api_key_encrypted"
        private const val API_KEY_ALIAS = "jkt48_connect_api_key"
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
        val encrypted = prefs.getString(KEY_API_KEY_ENCRYPTED, null)
        if (!encrypted.isNullOrBlank()) {
            return try {
                decryptApiKey(encrypted)
            } catch (_: Exception) {
                prefs.edit().remove(KEY_API_KEY_ENCRYPTED).apply()
                ""
            }
        }

        val stored = prefs.getString(KEY_API_KEY, null)
        if (stored.isNullOrBlank()) return ""

        // Migrate existing plaintext preference to device-bound encryption.
        return try {
            prefs.edit()
                .putString(KEY_API_KEY_ENCRYPTED, encryptApiKey(stored))
                .remove(KEY_API_KEY)
                .apply()
            stored
        } catch (_: Exception) {
            // Do not leave the legacy plaintext secret on disk if the keystore fails.
            prefs.edit().remove(KEY_API_KEY).apply()
            ""
        }
    }

    private fun loadBaseUrl(): String {
        val stored = prefs.getString(KEY_BASE_URL, null)
        return stored?.takeIf { it.isNotBlank() } ?: Jkt48ConnectApiClient.DEFAULT_BASE_URL
    }

    fun setApiKey(newKey: String): Boolean {
        val trimmed = newKey.trim()
        return try {
            if (trimmed.isBlank()) {
                prefs.edit()
                    .remove(KEY_API_KEY_ENCRYPTED)
                    .remove(KEY_API_KEY)
                    .apply()
            } else {
                prefs.edit()
                    .putString(KEY_API_KEY_ENCRYPTED, encryptApiKey(trimmed))
                    .remove(KEY_API_KEY)
                    .apply()
            }
            _apiKey.value = trimmed
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun encryptApiKey(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptApiKey(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(combined)
        val iv = ByteArray(12)
        require(buffer.remaining() > iv.size) { "Encrypted API key is invalid." }
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(API_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                API_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
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

