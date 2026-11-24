package com.example.idnliverecorder.monitoring.jkt48.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Objects for JKT48Connect API v3.
 *
 * Mapped to v3 response structure:
 * - name, img, img_alt, url_key, slug, room_id, is_graduate, is_group,
 *   chat_room_id, started_at, type, identifier, showId, streaming_url_list
 *
 * All fields are nullable to handle schema variations gracefully.
 */
data class Jkt48LiveItemDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("img_alt") val imgAlt: String? = null,
    @SerializedName("url_key") val urlKey: String? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("room_id") val rawRoomId: JsonElement? = null,
    @SerializedName("is_graduate") val isGraduate: Boolean? = null,
    @SerializedName("is_group") val isGroup: Boolean? = null,
    @SerializedName("chat_room_id") val rawChatRoomId: JsonElement? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("identifier") val identifier: String? = null,
    @SerializedName("showId") val rawShowId: JsonElement? = null,
    @SerializedName("streaming_url_list") val streamingUrlList: List<Jkt48StreamUrlDto>? = null,

    // Backward-compatible fallback fields (v2 / legacy wrappers)
    @SerializedName("id") val legacyId: String? = null,
    @SerializedName("member") val member: Jkt48MemberDto? = null,
    @SerializedName("user") val user: Jkt48UserDto? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("platform") val platform: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("live_at") val liveAt: String? = null,
    @SerializedName("viewers") val viewers: Long? = null
) {
    /**
     * Resolves room_id as String regardless of whether the JSON payload delivers
     * an integer, double, long, or string primitive.
     */
    val resolvedRoomId: String?
        get() = when {
            rawRoomId == null || rawRoomId.isJsonNull -> legacyId
            rawRoomId.isJsonPrimitive -> rawRoomId.asString
            else -> rawRoomId.toString().trim('"', ' ')
        }

    val resolvedChatRoomId: String?
        get() = when {
            rawChatRoomId == null || rawChatRoomId.isJsonNull -> null
            rawChatRoomId.isJsonPrimitive -> rawChatRoomId.asString
            else -> rawChatRoomId.toString().trim('"', ' ')
        }

    val resolvedShowId: String?
        get() = when {
            rawShowId == null || rawShowId.isJsonNull -> null
            rawShowId.isJsonPrimitive -> rawShowId.asString
            else -> rawShowId.toString().trim('"', ' ')
        }

    /**
     * Resolves member name using v3 `name`, falling back to member/user objects or title.
     */
    val resolvedMemberName: String
        get() = name?.ifBlank { null }
            ?: member?.name?.ifBlank { null }
            ?: user?.name?.ifBlank { null }
            ?: member?.nickname?.ifBlank { null }
            ?: title?.ifBlank { null }
            ?: "JKT48 Member"

    /**
     * Resolves the primary image / thumbnail URL with prioritized fallbacks:
     * v3 `img` -> `img_alt` -> `thumbnail` -> `image` -> `imageUrl` -> member img.
     */
    val resolvedThumbnailUrl: String?
        get() = img?.ifBlank { null }
            ?: imgAlt?.ifBlank { null }
            ?: thumbnail?.ifBlank { null }
            ?: image?.ifBlank { null }
            ?: imageUrl?.ifBlank { null }
            ?: member?.img?.ifBlank { null }
            ?: user?.avatarUrl?.ifBlank { null }

    /**
     * Resolves stream URLs from `streaming_url_list[].url`.
     */
    val resolvedStreamUrls: List<String>
        get() = streamingUrlList?.mapNotNull { it.url?.trim()?.ifBlank { null } } ?: emptyList()
}

data class Jkt48MemberDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("img_alt") val imgAlt: String? = null,
    @SerializedName("url") val url: String? = null
)

data class Jkt48UserDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("username") val username: String? = null
)

data class Jkt48StreamUrlDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("quality") val quality: Any? = null,
    @SerializedName("type") val type: String? = null
)
