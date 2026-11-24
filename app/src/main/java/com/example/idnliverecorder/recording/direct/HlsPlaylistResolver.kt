package com.example.idnliverecorder.recording.direct

import java.net.URI

/**
 * Robust HLS Playlist Resolver (Phase 8 Bug Fix).
 *
 * Responsibilities:
 * 1. Distinguishes Master Playlists (#EXT-X-STREAM-INF) from Media Playlists (#EXTINF / #EXT-X-TARGETDURATION).
 * 2. Parses Master Playlists and selects the highest compatible bandwidth variant.
 * 3. Resolves relative URLs (paths, parents, or root-relative) correctly against parent URLs.
 * 4. Extracts fragmented MP4 initialization segment (#EXT-X-MAP:URI="...") and MPEG-TS segments.
 * 5. Sanitizes URLs for secure logging (masks apikey, tokens, signatures).
 *
 * SAFETY:
 * - Does not perform DRM bypass or unauthorized stream extraction.
 * - Handles standard RFC 8216 HLS playlists.
 */
object HlsPlaylistResolver {

    data class VariantStream(
        val bandwidth: Long,
        val resolution: String? = null,
        val codecs: String? = null,
        val url: String
    )

    data class MediaSegment(
        val url: String,
        val durationSec: Double
    )

    data class ResolvedMediaPlaylist(
        val targetDurationSec: Long,
        val segments: List<MediaSegment>,
        val initSegmentUrl: String? = null,
        val isEndList: Boolean = false
    )

    /**
     * Represents an EXT-X-MEDIA audio rendition declared in a Master Playlist.
     */
    data class AudioRendition(
        val groupId: String,
        val name: String,
        val language: String? = null,
        val isDefault: Boolean = false,
        val url: String
    )

    /**
     * Checks if the playlist text represents an HLS Master Playlist.
     */
    fun isMasterPlaylist(playlistText: String): Boolean {
        return playlistText.contains("#EXT-X-STREAM-INF")
    }

    /**
     * Parses all variant streams from a Master Playlist text and resolves their URLs.
     */
    fun parseMasterPlaylist(playlistText: String, masterPlaylistUrl: String): List<VariantStream> {
        val variants = mutableListOf<VariantStream>()
        val lines = playlistText.lines()

        var pendingBandwidth: Long? = null
        var pendingResolution: String? = null
        var pendingCodecs: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                val attributes = trimmed.removePrefix("#EXT-X-STREAM-INF:")
                pendingBandwidth = extractAttributeLong(attributes, "BANDWIDTH")
                pendingResolution = extractAttributeString(attributes, "RESOLUTION")
                pendingCodecs = extractAttributeString(attributes, "CODECS")
            } else if (!trimmed.startsWith("#")) {
                if (pendingBandwidth != null) {
                    val resolvedUrl = resolveUrl(masterPlaylistUrl, trimmed)
                    variants.add(
                        VariantStream(
                            bandwidth = pendingBandwidth,
                            resolution = pendingResolution,
                            codecs = pendingCodecs,
                            url = resolvedUrl
                        )
                    )
                }
                pendingBandwidth = null
                pendingResolution = null
                pendingCodecs = null
            }
        }

        return variants
    }

    /**
     * Selects the optimal compatible stream variant (highest bandwidth).
     */
    fun selectOptimalVariant(variants: List<VariantStream>): VariantStream? {
        if (variants.isEmpty()) return null
        return variants.maxByOrNull { it.bandwidth }
    }

    /**
     * Parses all EXT-X-MEDIA TYPE=AUDIO renditions from a Master Playlist.
     * Returns a map of groupId → list of AudioRendition entries.
     */
    fun parseAudioRenditions(playlistText: String, masterPlaylistUrl: String): Map<String, List<AudioRendition>> {
        val result = mutableMapOf<String, MutableList<AudioRendition>>()
        for (line in playlistText.lines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#EXT-X-MEDIA:")) continue
            val attributes = trimmed.removePrefix("#EXT-X-MEDIA:")
            val type = extractAttributeString(attributes, "TYPE")
            if (!type.equals("AUDIO", ignoreCase = true)) continue
            val groupId = extractAttributeString(attributes, "GROUP-ID") ?: continue
            val name = extractAttributeString(attributes, "NAME") ?: groupId
            val language = extractAttributeString(attributes, "LANGUAGE")
            val isDefault = extractAttributeString(attributes, "DEFAULT").equals("YES", ignoreCase = true)
            val uriRaw = extractAttributeString(attributes, "URI") ?: continue
            val resolvedUrl = resolveUrl(masterPlaylistUrl, uriRaw)
            val rendition = AudioRendition(
                groupId = groupId,
                name = name,
                language = language,
                isDefault = isDefault,
                url = resolvedUrl
            )
            result.getOrPut(groupId) { mutableListOf() }.add(rendition)
        }
        return result
    }

    /**
     * Given a selected VariantStream and the full Master Playlist text, finds the matching
     * default audio rendition URL for the AUDIO group referenced by the variant.
     * Returns null if no audio group is declared or no matching rendition exists.
     */
    fun resolveAudioRenditionUrl(
        selectedVariant: VariantStream,
        masterPlaylistText: String,
        masterPlaylistUrl: String
    ): String? {
        // Find AUDIO attribute in the EXT-X-STREAM-INF line for the selected variant
        val lines = masterPlaylistText.lines()
        var captureNext = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                val attributes = trimmed.removePrefix("#EXT-X-STREAM-INF:")
                val bandwidth = extractAttributeLong(attributes, "BANDWIDTH")
                if (bandwidth == selectedVariant.bandwidth) {
                    captureNext = true
                }
                val audioGroupId = extractAttributeString(attributes, "AUDIO")
                if (audioGroupId != null && captureNext) {
                    val renditions = parseAudioRenditions(masterPlaylistText, masterPlaylistUrl)
                    val group = renditions[audioGroupId] ?: return null
                    return (group.firstOrNull { it.isDefault } ?: group.firstOrNull())?.url
                }
            } else if (!trimmed.startsWith("#") && captureNext) {
                captureNext = false
            }
        }
        return null
    }

    /**
     * Parses a Media Playlist into target duration, segment list, init segment, and endlist status.
     */
    fun parseMediaPlaylist(playlistText: String, mediaPlaylistUrl: String): ResolvedMediaPlaylist {
        var targetDuration = 2L
        var initSegmentUrl: String? = null
        var isEndList = false
        val segments = mutableListOf<MediaSegment>()

        val targetDurationMatch = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(playlistText)
        if (targetDurationMatch != null) {
            targetDuration = targetDurationMatch.groupValues[1].toLongOrNull() ?: 2L
        }

        // Check for fragmented MP4 initialization segment: #EXT-X-MAP:URI="init.mp4"
        val mapMatch = Regex("#EXT-X-MAP:URI=[\"']?([^\"'\\s,]+)[\"']?").find(playlistText)
        if (mapMatch != null) {
            val rawUri = mapMatch.groupValues[1]
            initSegmentUrl = resolveUrl(mediaPlaylistUrl, rawUri)
        }

        if (playlistText.contains("#EXT-X-ENDLIST")) {
            isEndList = true
        }

        val lines = playlistText.lines()
        var currentDuration = 2.0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:")) {
                val durStr = trimmed.removePrefix("#EXTINF:").substringBefore(",").trim()
                currentDuration = durStr.toDoubleOrNull() ?: 2.0
            } else if (!trimmed.startsWith("#")) {
                val resolvedUrl = resolveUrl(mediaPlaylistUrl, trimmed)
                segments.add(MediaSegment(url = resolvedUrl, durationSec = currentDuration))
            }
        }

        return ResolvedMediaPlaylist(
            targetDurationSec = targetDuration,
            segments = segments,
            initSegmentUrl = initSegmentUrl,
            isEndList = isEndList
        )
    }

    /**
     * Resolves a relative or absolute URL against the base URL.
     */
    fun resolveUrl(baseUrl: String, relativeOrAbsoluteUrl: String): String {
        val trimmed = relativeOrAbsoluteUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (_: Exception) {
            val baseWithoutQuery = baseUrl.substringBefore("?")
            val parent = baseWithoutQuery.substringBeforeLast("/") + "/"
            parent + trimmed
        }
    }

    /**
     * Sanitizes URLs for logging by masking sensitive query parameters (e.g. apikey, token, auth, secret).
     */
    fun sanitizeUrl(url: String): String {
        return try {
            val uri = URI(url)
            val query = uri.query
            if (query.isNullOrBlank()) {
                "${uri.scheme}://${uri.host ?: ""}${uri.path ?: ""}"
            } else {
                val sensitiveKeys = setOf("apikey", "key", "token", "auth", "secret", "signature", "sig", "credential", "dna")
                val maskedQuery = query.split("&").joinToString("&") { param ->
                    val parts = param.split("=", limit = 2)
                    val key = parts[0]
                    if (key.lowercase() in sensitiveKeys) {
                        "$key=***"
                    } else {
                        param
                    }
                }
                "${uri.scheme}://${uri.host ?: ""}${uri.path ?: ""}?$maskedQuery"
            }
        } catch (_: Exception) {
            url.substringBefore("?")
        }
    }

    private fun extractAttributeLong(attributes: String, key: String): Long? {
        val match = Regex("$key=(\\d+)").find(attributes)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractAttributeString(attributes: String, key: String): String? {
        val matchWithQuotes = Regex("$key=[\"']([^\"']+)[\"']").find(attributes)
        if (matchWithQuotes != null) {
            return matchWithQuotes.groupValues[1]
        }
        val matchWithoutQuotes = Regex("$key=([^,;\\s]+)").find(attributes)
        return matchWithoutQuotes?.groupValues?.get(1)
    }
}
