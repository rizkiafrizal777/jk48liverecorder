package com.example.idnliverecorder.monitoring.jkt48

import android.content.Context
import android.util.Log
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48ConnectApiClient
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48LiveItemDto
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for fetching, parsing, and filtering JKT48 IDN Live streams.
 *
 * Resilience:
 * - Tolerates missing fields, different root structures (Array vs Object with data wrapper),
 *   and empty payloads.
 * - Strictly filters for IDN Live platform streams.
 * - Sanitizes Logcat output so API keys are never printed.
 */
class Jkt48ConnectRepository(
    private val context: Context,
    private val favoritesRepository: Jkt48FavoritesRepository = Jkt48FavoritesRepository(context),
    private val preferences: Jkt48LivePreferences = Jkt48LivePreferences(context),
    private val notificationHelper: Jkt48NotificationHelper = Jkt48NotificationHelper(context)
) {
    companion object {
        private const val TAG = "Jkt48ConnectRepo"
    }

    private val gson = Gson()

    /**
     * Fetches current IDN Live streams for JKT48 members.
     * @param apiKey Optional override for API key. If null, reads from preferences/local config.
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
            val response = api.getIdnLive(apiKey = key)

            if (!response.isSuccessful) {
                val errorCode = response.code()
                val errorMsg = when (errorCode) {
                    401 -> "API Key tidak valid atau kedaluwarsa (401 Unauthorized)."
                    403 -> "Akses ditolak (403 Forbidden). Kuota atau hak akses API Key tidak mencukupi."
                    404 -> "Endpoint live tidak ditemukan (404 Not Found)."
                    429 -> "Terlalu banyak permintaan (429 Too Many Requests). Tunggu beberapa saat."
                    in 500..599 -> "Server JKT48Connect sedang bermasalah ($errorCode)."
                    else -> "Gagal memuat live stream ($errorCode: ${response.message()})"
                }
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            val jsonBody: JsonElement? = response.body()
            if (jsonBody == null || jsonBody.isJsonNull) {
                return@withContext Result.success(emptyList())
            }

            val membersList = parseJsonToMembers(jsonBody)

            // Check and notify for favorite members
            if (preferences.notifyFavorites.value) {
                checkAndNotifyFavorites(membersList)
            }

            Log.d(TAG, "Successfully parsed ${membersList.size} IDN Live members.")
            Result.success(membersList)

        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching JKT48 Live: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Flexible parser handling direct array or wrapped data object.
     */
    private fun parseJsonToMembers(json: JsonElement): List<Jkt48LiveMember> {
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

                // Requirement 5: Filter only data with platform IDN
                val platform = (dto.platform ?: dto.type ?: "idn").lowercase()
                val isIdn = platform.contains("idn") || (dto.type?.contains("idn", ignoreCase = true) == true)
                if (!isIdn) continue

                // Resolve member name with multiple fallbacks
                val memberName = dto.member?.name
                    ?: dto.user?.name
                    ?: dto.name
                    ?: dto.member?.nickname
                    ?: dto.title
                    ?: "JKT48 Member"

                // Resolve room_id with fallbacks
                val roomId = dto.roomId
                    ?: dto.id
                    ?: dto.slug
                    ?: memberName.hashCode().toString()

                // Resolve thumbnail
                val thumbnail = dto.thumbnail
                    ?: dto.image
                    ?: dto.imageUrl
                    ?: dto.member?.img
                    ?: dto.user?.avatarUrl
                    ?: dto.member?.imgAlt

                // Streaming URLs
                val streamUrls = dto.streamingUrlList?.mapNotNull { it.url?.ifBlank { null } } ?: emptyList()

                val member = Jkt48LiveMember(
                    roomId = roomId,
                    memberName = memberName,
                    slug = dto.slug,
                    title = dto.title,
                    thumbnail = thumbnail,
                    startedAt = dto.startedAt ?: dto.liveAt,
                    startedAtMs = parseStartedAtToMs(dto.startedAt ?: dto.liveAt),
                    type = dto.type ?: "idn",
                    platform = "idn",
                    streamingUrls = streamUrls,
                    viewers = dto.viewers,
                    isFavorite = favoritesRepository.isFavorite(memberName)
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
            // If already a numeric timestamp
            startedAtStr.toLongOrNull()
        } catch (e: Exception) {
            null
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

