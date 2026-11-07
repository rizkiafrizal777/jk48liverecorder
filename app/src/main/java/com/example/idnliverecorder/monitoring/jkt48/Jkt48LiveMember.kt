package com.example.idnliverecorder.monitoring.jkt48

/**
 * Domain representation of an active JKT48 member live stream on IDN Live.
 */
data class Jkt48LiveMember(
    val roomId: String,
    val memberName: String,
    val slug: String?,
    val title: String?,
    val thumbnail: String?,
    val startedAt: String?,
    val startedAtMs: Long?,
    val type: String?,
    val platform: String,
    val streamingUrls: List<String> = emptyList(),
    val viewers: Long? = null,
    val isFavorite: Boolean = false
) {
    /**
     * Unique identifier for live session deduplication.
     */
    val sessionKey: String
        get() = "${roomId}_${startedAt ?: slug ?: memberName}"
}

