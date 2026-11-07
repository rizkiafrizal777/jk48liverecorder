package com.example.idnliverecorder.monitoring.jkt48.api

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Objects for JKT48Connect API.
 * All fields are nullable to handle schema variations gracefully.
 */
data class Jkt48LiveItemDto(
    @SerializedName("room_id") val roomId: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("member") val member: Jkt48MemberDto? = null,
    @SerializedName("user") val user: Jkt48UserDto? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("platform") val platform: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("img_alt") val imgAlt: String? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("live_at") val liveAt: String? = null,
    @SerializedName("viewers") val viewers: Long? = null,
    @SerializedName("streaming_url_list") val streamingUrlList: List<Jkt48StreamUrlDto>? = null
)

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

