package com.sl.watchrelay.matching

import java.text.Normalizer
import java.util.Locale

object MetadataNormalizer {
    private val seasonEpisodePatterns = listOf(
        Regex("(?i)(?:^|[\\s._-])s(\\d{1,2})[\\s._-]*e(\\d{1,3})(?:$|[\\s._-])"),
        Regex("(?i)(?:^|[\\s._-])(\\d{1,2})x(\\d{1,3})(?:$|[\\s._-])"),
        Regex("(?i)(?:^|[\\s._-])(\\d{1,2})[\\s._-]*(?:season|сезон)[\\s._-]+(\\d{1,3})[\\s._-]*(?:episode|ep|серия)(?:$|[\\s._-])"),
    )
    private val yearPattern = Regex("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)")
    private val extensionPattern = Regex("(?i)\\.(mkv|mp4|avi|mov|m4v|webm|ts|m2ts)$")
    private val releaseNoise = setOf(
        "360p", "480p", "576p", "720p", "1080p", "1080i", "1440p", "2160p", "4320p",
        "web", "webdl", "webrip", "bluray", "bdrip", "brrip", "hdrip", "dvdrip", "hdtv",
        "x264", "x265", "h264", "h265", "hevc", "av1", "hdr", "hdr10", "dv", "dolbyvision",
        "aac", "ac3", "eac3", "dts", "atmos", "remux",
    )

    fun normalize(metadata: PlaybackMetadata): NormalizedMetadata {
        val sourceTexts = listOfNotNull(metadata.title, metadata.subtitle, metadata.originalTitle)
        val parsedEpisode = sourceTexts.firstNotNullOfOrNull(::parseSeasonEpisode)
        val season = metadata.season ?: parsedEpisode?.first
        val episode = metadata.episode ?: parsedEpisode?.second
        val inferredKind = when {
            metadata.mediaKind != SourceMediaKind.UNKNOWN -> metadata.mediaKind
            season != null && episode != null -> SourceMediaKind.EPISODE
            else -> SourceMediaKind.UNKNOWN
        }
        val parsedYear = metadata.year ?: sourceTexts.firstNotNullOfOrNull(::parseYear)
        val title = cleanTitle(metadata.title, parsedYear)
        val originalTitle = cleanTitle(metadata.originalTitle, parsedYear)

        return NormalizedMetadata(
            title = title,
            titleKey = title?.let(::comparisonKey),
            originalTitle = originalTitle,
            originalTitleKey = originalTitle?.let(::comparisonKey),
            year = parsedYear?.takeIf { it in 1900..2100 },
            season = season?.takeIf { it >= 0 },
            episode = episode?.takeIf { it > 0 },
            imdbId = normalizeImdbId(metadata.imdbId),
            kinopoiskId = metadata.kinopoiskId?.trim()?.takeIf(String::isNotBlank),
            mediaKind = inferredKind,
        )
    }

    fun comparisonKey(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        return decomposed
            .lowercase(Locale.ROOT)
            .replace(Regex("\\p{M}+"), "")
            .replace('&', ' ')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun cleanTitle(raw: String?, parsedYear: Int?): String? {
        if (raw.isNullOrBlank()) return null
        var value = extensionPattern.replace(raw.trim(), "")
        val marker = seasonEpisodePatterns
            .mapNotNull { it.find(value)?.range?.first }
            .minOrNull()
        if (marker != null) value = value.substring(0, marker)

        value = value
            .replace('.', ' ')
            .replace('_', ' ')
            .replace(Regex("[\\[\\]{}()]"), " ")

        if (parsedYear != null) {
            value = value.replace(Regex("(?<!\\d)$parsedYear(?!\\d)"), " ")
        }

        val tokens = value.split(Regex("\\s+|-+"))
        val kept = ArrayList<String>(tokens.size)
        for (token in tokens) {
            val normalized = comparisonKey(token).replace(" ", "")
            if (kept.isNotEmpty() && isReleaseNoise(normalized)) break
            if (token.isNotBlank()) kept += token
        }

        return kept.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '.', '_')
            .takeIf(String::isNotBlank)
    }

    private fun parseSeasonEpisode(value: String): Pair<Int, Int>? {
        seasonEpisodePatterns.forEach { pattern ->
            val match = pattern.find(value) ?: return@forEach
            val season = match.groupValues[1].toIntOrNull() ?: return@forEach
            val episode = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (season >= 0 && episode > 0) return season to episode
        }
        return null
    }

    private fun parseYear(value: String): Int? = yearPattern.findAll(value)
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .lastOrNull()

    private fun normalizeImdbId(value: String?): String? {
        val trimmed = value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank) ?: return null
        return if (trimmed.startsWith("tt")) trimmed else trimmed.toLongOrNull()?.let { "tt$it" } ?: trimmed
    }

    private fun isReleaseNoise(token: String): Boolean {
        if (token in releaseNoise) return true
        return token.matches(Regex("(?:x|h)26[45]")) ||
            token.matches(Regex("\\d{3,4}p")) ||
            token.matches(Regex("(?:web|blu|bd|dvd|hd).*(?:rip|dl)"))
    }
}
