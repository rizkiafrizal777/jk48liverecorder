package com.example.idnliverecorder.recording.validation

import android.net.Uri
import android.util.Log
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Validates direct livestream sources for JKT48 IDN Live (Phase 5).
 *
 * SAFETY & POLICY RULES:
 * - No reverse-engineering of internal IDN endpoints.
 * - No extraction of auth tokens, cookies, or signatures from the IDN app.
 * - No traffic interception or packet manipulation.
 * - No DRM or signature bypass.
 * - No access to unauthorized private streams.
 * - If authorization is not explicitly granted, recording is strictly locked at AUTHORIZATION_REQUIRED.
 */
class StreamSourceValidator(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    companion object {
        private const val TAG = "StreamSourceValidator"
        const val PROVIDER_JKT48CONNECT = "jkt48connect"
        const val PROVIDER_OFFICIAL_IDN = "official_idn"
        const val PROVIDER_TEST_MEDIA = "authorized_test_media"
    }

    enum class StreamMediaFormat(val label: String) {
        HLS("HLS (.m3u8)"),
        MP4("Direct MP4"),
        UNKNOWN("Format Tidak Dikenal"),
        NONE("Tidak Ada Sumber")
    }

    data class ValidationResult(
        val status: DirectStreamStatus,
        val format: StreamMediaFormat,
        val isFormatSupported: Boolean,
        val isSourceActive: Boolean,
        val isAuthorized: Boolean,
        val message: String,
        val httpStatusCode: Int? = null,
        val streamUrl: String? = null
    ) {
        val canRecord: Boolean get() = status == DirectStreamStatus.READY_TO_RECORD
    }

    /**
     * Validates a stream source URL, checks format, connectivity, and authorization.
     *
     * @param url The raw streaming URL (if provided by API).
     * @param provider The provider identifier (e.g. PROVIDER_JKT48CONNECT).
     * @param isRecordingAuthorized Explicit authorization flag from verified agreement.
     */
    suspend fun validate(
        url: String?,
        provider: String = PROVIDER_JKT48CONNECT,
        isRecordingAuthorized: Boolean = false
    ): ValidationResult = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) {
            return@withContext ValidationResult(
                status = DirectStreamStatus.SOURCE_UNAVAILABLE,
                format = StreamMediaFormat.NONE,
                isFormatSupported = false,
                isSourceActive = false,
                isAuthorized = false,
                message = "URL stream tidak tersedia pada metadata siaran saat ini."
            )
        }

        // 1. Identify format
        val format = identifyFormat(url)
        val isHls = format == StreamMediaFormat.HLS
        if (!isHls && format != StreamMediaFormat.MP4) {
            return@withContext ValidationResult(
                status = DirectStreamStatus.SOURCE_UNAVAILABLE,
                format = format,
                isFormatSupported = false,
                isSourceActive = false,
                isAuthorized = false,
                message = "Format media tidak didukung. Direct Stream hanya mendukung live HLS (.m3u8) atau MP4.",
                streamUrl = url
            )
        }

        // 2. Probe connection without downloading full media
        val (isReachable, statusCode, errorMsg) = probeStreamConnection(url)
        if (!isReachable) {
            val status = if (statusCode == 401 || statusCode == 403) {
                DirectStreamStatus.AUTHORIZATION_REQUIRED
            } else {
                DirectStreamStatus.SOURCE_UNAVAILABLE
            }
            return@withContext ValidationResult(
                status = status,
                format = format,
                isFormatSupported = isHls,
                isSourceActive = false,
                isAuthorized = false,
                message = errorMsg ?: "Sumber stream tidak aktif atau koneksi terputus (HTTP $statusCode).",
                httpStatusCode = statusCode,
                streamUrl = url
            )
        }

        // 3. Authorization check (Rule: Metadata != Recording rights)
        if (!isRecordingAuthorized) {
            Log.w(TAG, "Stream source active, but recording authorization is not verified.")
            return@withContext ValidationResult(
                status = DirectStreamStatus.AUTHORIZATION_REQUIRED,
                format = format,
                isFormatSupported = isHls,
                isSourceActive = true,
                isAuthorized = false,
                message = "WAITING FOR AUTHORIZATION — Hak perekaman direct stream belum diotorisasi oleh pemegang hak cipta.",
                httpStatusCode = statusCode,
                streamUrl = url
            )
        }

        // 4. Source is available, active, HLS, and authorized
        ValidationResult(
            status = DirectStreamStatus.READY_TO_RECORD,
            format = format,
            isFormatSupported = true,
            isSourceActive = true,
            isAuthorized = true,
            message = "Sumber HLS valid dan siap untuk direct recording.",
            httpStatusCode = statusCode,
            streamUrl = url
        )
    }

    /**
     * Determines whether a given URL points to an HLS (.m3u8) or other format.
     */
    fun identifyFormat(url: String?): StreamMediaFormat {
        if (url.isNullOrBlank()) return StreamMediaFormat.NONE
        val cleanUrl = url.substringBefore("?").lowercase()
        return when {
            cleanUrl.endsWith(".m3u8") || cleanUrl.contains(".m3u8") -> StreamMediaFormat.HLS
            cleanUrl.endsWith(".mp4") -> StreamMediaFormat.MP4
            else -> StreamMediaFormat.UNKNOWN
        }
    }

    /**
     * Performs a lightweight probe to verify stream responsiveness.
     */
    private fun probeStreamConnection(url: String): Triple<Boolean, Int?, String?> {
        // Special case for local / mock test streams used in unit testing
        if (url.startsWith("test://") || url.startsWith("mock://") || url.startsWith("file://")) {
            return Triple(true, 200, null)
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "IDNLiveRecorder/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (response.isSuccessful) {
                    Triple(true, code, null)
                } else if (code == 405) {
                    // Some HLS servers disallow HEAD; try GET with range header for first 512 bytes
                    probeWithGetRange(url)
                } else if (code == 401 || code == 403) {
                    Triple(false, code, "Akses stream ditolak (HTTP $code). Otorisasi diperlukan.")
                } else if (code == 404 || code == 410) {
                    Triple(false, code, "Stream telah berakhir atau URL kedaluwarsa (HTTP $code).")
                } else {
                    Triple(false, code, "Server stream merespons status HTTP $code.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection probe failed for stream: ${e.message}")
            Triple(false, null, "Gagal terhubung ke host stream: ${e.localizedMessage}")
        }
    }

    private fun probeWithGetRange(url: String): Triple<Boolean, Int?, String?> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-512")
                .header("User-Agent", "IDNLiveRecorder/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    Triple(true, response.code, null)
                } else {
                    Triple(false, response.code, "HTTP ${response.code} pada GET probe.")
                }
            }
        } catch (e: Exception) {
            Triple(false, null, "Probe fallback gagal: ${e.message}")
        }
    }
}
