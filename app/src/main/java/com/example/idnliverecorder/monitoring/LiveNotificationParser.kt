package com.example.idnliverecorder.monitoring

import java.util.Locale

sealed interface ParseResult {
    data class Match(
        val streamerName: String,
        val fullContent: String,
        val matchedKeyword: String
    ) : ParseResult

    data object PackageMismatch : ParseResult
    data object NoKeywordMatch : ParseResult
    data object EmptyContent : ParseResult
}

object LiveNotificationParser {

    private val GENERIC_APP_NAMES = setOf(
        "idn", "idn live", "idn app", "idn media", "live", "live stream", "notification"
    )

    fun parse(
        packageName: String,
        targetPackageName: String?,
        title: String?,
        text: String?,
        bigText: String?,
        keywords: List<String>
    ): ParseResult {
        // 1. Verify package name
        if (targetPackageName.isNullOrBlank() || !packageName.equals(targetPackageName.trim(), ignoreCase = true)) {
            return ParseResult.PackageMismatch
        }

        // 2. Validate content presence
        val cleanTitle = title?.trim().orEmpty()
        val cleanText = text?.trim().orEmpty()
        val cleanBigText = bigText?.trim().orEmpty()

        if (cleanTitle.isEmpty() && cleanText.isEmpty() && cleanBigText.isEmpty()) {
            return ParseResult.EmptyContent
        }

        // Combine all texts for keyword search
        val combinedContent = buildString {
            if (cleanTitle.isNotEmpty()) append(cleanTitle)
            if (cleanText.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append(cleanText)
            }
            if (cleanBigText.isNotEmpty() && cleanBigText != cleanText) {
                if (isNotEmpty()) append(" — ")
                append(cleanBigText)
            }
        }

        val contentLower = combinedContent.lowercase(Locale.ROOT)

        // 3. Check for matching keyword (prioritize longer/more specific phrases first)
        val matchedKeyword = keywords
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
            .firstOrNull { kw ->
                // Check word boundary or substring match
                contentLower.contains(kw)
            } ?: return ParseResult.NoKeywordMatch

        // 4. Extract streamer name
        val streamerName = extractStreamerName(
            title = cleanTitle,
            text = cleanText,
            bigText = cleanBigText,
            matchedKeyword = matchedKeyword
        )

        return ParseResult.Match(
            streamerName = streamerName,
            fullContent = combinedContent,
            matchedKeyword = matchedKeyword
        )
    }

    private fun extractStreamerName(
        title: String,
        text: String,
        bigText: String,
        matchedKeyword: String
    ): String {
        val titleLower = title.lowercase(Locale.ROOT)
        val isTitleGeneric = GENERIC_APP_NAMES.any { it == titleLower || titleLower.startsWith("$it ") }

        // Strategy A: If title is not generic and doesn't contain the keyword,
        // the title itself is usually the streamer's name (e.g. Title: "Freya Jayawardana", Text: "Sedang live!")
        if (title.isNotEmpty() && !isTitleGeneric && !titleLower.contains(matchedKeyword)) {
            return cleanExtractedName(title)
        }

        // Strategy B: Pattern match in text or title: "StreamerName sedang live..." or "Live dari StreamerName"
        val candidates = listOf(title, text, bigText).filter { it.isNotEmpty() }
        for (candidate in candidates) {
            // Pattern: "[Name] sedang live / is live / started a live / live ..."
            val regexPrefix = Regex(
                """^(?:🔴\s*|live:\s*)?([^\n•–—:!?,]{2,40}?)\s+(?:sedang\s+live|is\s+live|started\s+a\s+live|lagi\s+live|live\b)""",
                RegexOption.IGNORE_CASE
            )
            val matchPrefix = regexPrefix.find(candidate)
            if (matchPrefix != null) {
                val candidateName = matchPrefix.groupValues[1].trim()
                if (!isGenericName(candidateName)) {
                    return cleanExtractedName(candidateName)
                }
            }

            // Pattern: "Live dari [Name]: ..."
            val regexLiveDari = Regex(
                """live\s+dari\s+([^\n•–—:!?,]{2,40})""",
                RegexOption.IGNORE_CASE
            )
            val matchLiveDari = regexLiveDari.find(candidate)
            if (matchLiveDari != null) {
                val candidateName = matchLiveDari.groupValues[1].trim()
                if (!isGenericName(candidateName)) {
                    return cleanExtractedName(candidateName)
                }
            }
        }

        // Strategy C: If title is present and not completely generic, use title
        if (title.isNotEmpty() && !isTitleGeneric) {
            return cleanExtractedName(title)
        }

        // Fallback
        return "IDN Streamer"
    }

    private fun isGenericName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT).trim()
        return GENERIC_APP_NAMES.contains(lower) || lower.isEmpty() || lower.length < 2
    }

    private fun cleanExtractedName(name: String): String {
        return name
            .replace(Regex("""^[\s"“'(\[🔴]+"""), "")
            .replace(Regex("""[\s"”')\]!?:.]+$"""), "")
            .trim()
            .ifEmpty { "IDN Streamer" }
    }
}
