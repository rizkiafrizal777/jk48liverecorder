package com.example.idnliverecorder.monitoring.jkt48

import android.content.Context
import android.util.Log
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48ConnectApiClient
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48LiveItemDto
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Repository responsible for fetching, parsing, and filtering JKT48 live streams
 * from the JKT48Connect API v3.
 *
 * Requirements:
 * - Base URL: https://v3.jkt48connect.com
 * - Endpoint: /api/jkt48/live?apikey=YOUR_API_KEY
 * - Filters live IDN using type == "idn".
 * - Extracts stream URLs from streaming_url_list[].url (without auto-recording).
 * - Comprehensive HTTP status & network error handling (401/403, 404, 429, 5xx, timeout, malformed JSON).
 * - Sanitizes Logcat output so API keys are never leaked.
 */
class Jkt48ConnectRepository(
    private val context: Context,
    private val favoritesRepository: Jkt48FavoritesRepository = Jkt48FavoritesRepository(context),
    private val preferences: Jkt48LivePreferences = Jkt48LivePreferences(context),
    private val notificationHelper: Jkt48NotificationHelper = Jkt48NotificationHelper(context)
) {
    companion object {
        private const val TAG = "Jkt48ConnectRepo"
        private val gson = Gson()

        /**
         * Parses a JsonElement (array or wrapped object) into a filtered list of Jkt48LiveMember
         * matching only IDN Live streams (type == "idn").
         */
        fun parseJsonToMembers(
            json: JsonElement?,
            isFavoriteChecker: (String) -> Boolean = { false }
        ): List<Jkt48LiveMember> {
            if (json == null || json.isJsonNull) return emptyList()

            val itemsArray: JsonArray = when {
                json.isJsonArray -> json.asJsonArray
                json.isJsonObject -> {
                    val obj = json.asJsonObject
                    when {
                        obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data")
                        obj.has("live") && obj.get("live").isJsonArray -> obj.getAsJsonArray("live")
                        obj.has("idn") && obj.get("idn").isJsonArray -> obj.getAsJsonArray("idn")
                        else -> JsonArray()
                    }
                }
                else -> JsonArray()
            }

            val result = mutableListOf<Jkt48LiveMember>()

            for (elem in itemsArray) {
                if (!elem.isJsonObject) continue
                try {
                    val dto = gson.fromJson(elem, Jkt48LiveItemDto::class.java)

                    // Requirement 2: Filter live IDN menggunakan type == "idn"
                    val type = dto.type?.trim()?.lowercase()
                    val isIdn = type == "idn" || (type == null && dto.platform?.contains("idn", ignoreCase = true) == true)
                    if (!isIdn) continue

                    // Resolve member name with v3 priority
                    val memberName = dto.resolvedMemberName

                    // Resolve room_id with v3 priority
                    val roomId = dto.resolvedRoomId
                        ?: dto.slug
                        ?: dto.identifier
                        ?: memberName.hashCode().toString()

                    // Resolve thumbnail with v3 img / img_alt priority
                    val thumbnail = dto.resolvedThumbnailUrl

                    // Resolve streaming URLs from streaming_url_list
                    val streamUrls = dto.resolvedStreamUrls

                    val member = Jkt48LiveMember(
                        roomId = roomId,
                        memberName = memberName,
                        slug = dto.slug ?: dto.urlKey,
                        title = dto.title,
                        thumbnail = thumbnail,
                        startedAt = dto.startedAt ?: dto.liveAt,
                        startedAtMs = parseStartedAtToMs(dto.startedAt ?: dto.liveAt),
                        type = dto.type ?: "idn",
                        platform = "idn",
                        streamingUrls = streamUrls,
                        viewers = dto.viewers,
                        isFavorite = isFavoriteChecker(memberName)
                    )

                    result.add(member)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing item: ${e.message}")
                }
            }

            return result
        }

        private fun parseStartedAtToMs(startedAtStr: String?): Long? {
            if (startedAtStr.isNullOrBlank()) return null
            return try {
                startedAtStr.toLongOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Fetches current IDN Live streams for JKT48 members from JKT48Connect API.
     * @param apiKey Optional override for API key. If null, reads from local preferences.
     */
    suspend fun fetchIdnLiveStreams(apiKey: String? = null): Result<List<Jkt48LiveMember>> = withContext(Dispatchers.IO) {
        val key = apiKey?.trim() ?: preferences.apiKey.value.trim()

        if (key.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("API Key belum dikonfigurasi. Masukkan API Key JKT48Connect di pengaturan.")
            )
        }

        try {
            val api = Jkt48ConnectApiClient.create(preferences.baseUrl.value)
            val response = api.getLiveStreams(apiKey = key)

            if (!response.isSuccessful) {
                val errorCode = response.code()
                val errorMsg = when (errorCode) {
                    401 -> "API Key tidak valid atau kedaluwarsa (HTTP 401 Unauthorized)."
                    403 -> "Akses ditolak (HTTP 403 Forbidden). Kuota atau hak akses API Key tidak mencukupi."
                    404 -> "Endpoint /api/jkt48/live tidak ditemukan (HTTP 404 Not Found)."
                    429 -> "Terlalu banyak permintaan (HTTP 429 Too Many Requests). Tunggu beberapa saat."
                    503 -> "Server JKT48Connect sedang tidak tersedia (HTTP 503). Tunggu sebentar lalu coba muat ulang."
                    in 500..599 -> "Server JKT48Connect sedang bermasalah (HTTP $errorCode)."
                    else -> "Gagal memuat live stream ($errorCode: ${response.message()})"
                }
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            val jsonBody: JsonElement? = response.body()
            if (jsonBody == null || jsonBody.isJsonNull) {
                return@withContext Result.success(emptyList())
            }

            val membersList = parseJsonToMembers(jsonBody) { memberName ->
                favoritesRepository.isFavorite(memberName)
            }

            // Check and notify for favorite members
            if (preferences.notifyFavorites.value) {
                checkAndNotifyFavorites(membersList)
            }

            Log.d(TAG, "Successfully parsed ${membersList.size} IDN Live members from v3 API.")
            Result.success(membersList)

        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout connecting to JKT48Connect API.")
            Result.failure(SocketTimeoutException("Koneksi ke JKT48Connect timeout. Silakan periksa koneksi internet Anda."))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host for JKT48Connect API.")
            Result.failure(UnknownHostException("Tidak dapat terhubung ke server JKT48Connect. Periksa koneksi internet Anda."))
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Malformed JSON syntax from JKT48Connect API: ${e.message}")
            Result.failure(IllegalStateException("Format data respons JSON tidak sesuai struktur."))
        } catch (e: IOException) {
            Log.e(TAG, "Network I/O error fetching JKT48 Live: ${e.javaClass.simpleName}")
            Result.failure(IOException("Gagal terhubung ke jaringan. Periksa koneksi lalu coba lagi."))
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching JKT48 Live: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    private fun checkAndNotifyFavorites(liveMembers: List<Jkt48LiveMember>) {
        for (member in liveMembers) {
            if (member.isFavorite && !preferences.hasNotifiedSession(member.sessionKey)) {
                Log.d(TAG, "Favorite member is LIVE: ${member.memberName}. Triggering notification.")
                notificationHelper.notifyFavoriteLive(member)
                preferences.markSessionNotified(member.sessionKey)
            }
        }
    }
}
