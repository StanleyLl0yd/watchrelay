package com.sl.watchrelay.playback

import android.content.Context
import android.content.Intent
import com.sl.watchrelay.matching.PlaybackMetadata
import com.sl.watchrelay.matching.SourceMediaKind
import org.json.JSONObject

object IntentPlaybackMetadataExtractor {
    fun extract(intent: Intent): PlaybackMetadata {
        val values = intent.extras?.keySet().orEmpty().associateWith { intent.extras?.get(it) }
        return extract(values)
    }

    internal fun extract(values: Map<String, Any?>): PlaybackMetadata {
        val normalized = values.entries.associate { normalizeKey(it.key) to it.value }
        return PlaybackMetadata(
            title = text(normalized, TITLE_KEYS),
            originalTitle = text(normalized, ORIGINAL_TITLE_KEYS),
            year = number(normalized, YEAR_KEYS)?.takeIf { it in 1900..2100 },
            season = number(normalized, SEASON_KEYS)?.takeIf { it >= 0 },
            episode = number(normalized, EPISODE_KEYS)?.takeIf { it > 0 },
            imdbId = text(normalized, IMDB_KEYS),
            kinopoiskId = text(normalized, KINOPOISK_KEYS),
            mediaKind = mediaKind(text(normalized, TYPE_KEYS)),
        )
    }

    private fun text(values: Map<String, Any?>, keys: Set<String>): String? = keys
        .asSequence()
        .mapNotNull(values::get)
        .mapNotNull(::safeText)
        .firstOrNull()

    private fun number(values: Map<String, Any?>, keys: Set<String>): Int? = keys
        .asSequence()
        .mapNotNull(values::get)
        .mapNotNull { value ->
            when (value) {
                is Number -> value.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
        }
        .firstOrNull()

    private fun safeText(value: Any?): String? {
        val text = when (value) {
            is String -> value
            is CharSequence -> value.toString()
            is Number -> value.toString()
            else -> return null
        }.trim()
        if (text.isBlank() || SENSITIVE_VALUE.containsMatchIn(text)) return null
        return text.take(MAX_TEXT_LENGTH)
    }

    private fun mediaKind(value: String?): SourceMediaKind = when (value?.lowercase()?.trim()) {
        "movie", "film", "cinema" -> SourceMediaKind.MOVIE
        "episode", "serial", "series", "tv", "show" -> SourceMediaKind.EPISODE
        else -> SourceMediaKind.UNKNOWN
    }

    private fun normalizeKey(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

    private val TITLE_KEYS = setOf("title", "name", "movietitle", "videotitle", "androidintentextratitle")
    private val ORIGINAL_TITLE_KEYS = setOf("originaltitle", "titleoriginal", "originalname")
    private val YEAR_KEYS = setOf("year", "releaseyear")
    private val SEASON_KEYS = setOf("season", "seasonnumber")
    private val EPISODE_KEYS = setOf("episode", "episodenumber")
    private val IMDB_KEYS = setOf("imdb", "imdbid")
    private val KINOPOISK_KEYS = setOf("kinopoisk", "kinopoiskid", "kpid")
    private val TYPE_KEYS = setOf("type", "mediatype", "contenttype", "kind")
    private val SENSITIVE_VALUE = Regex("(?i)^(https?://|magnet:|content://|file://)")
    private const val MAX_TEXT_LENGTH = 300
}

data class BridgeMetadata(
    val targetPackage: String,
    val createdAtMs: Long,
    val metadata: PlaybackMetadata,
)

class BridgeMetadataStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(targetPackage: String, metadata: PlaybackMetadata, createdAtMs: Long) {
        if (!hasUsefulMetadata(metadata)) {
            preferences.edit().remove(KEY_VALUE).commit()
            return
        }
        val root = JSONObject()
            .put("targetPackage", targetPackage)
            .put("createdAtMs", createdAtMs)
            .putNullable("title", metadata.title)
            .putNullable("subtitle", metadata.subtitle)
            .putNullable("originalTitle", metadata.originalTitle)
            .putNullable("year", metadata.year)
            .putNullable("season", metadata.season)
            .putNullable("episode", metadata.episode)
            .putNullable("imdbId", metadata.imdbId)
            .putNullable("kinopoiskId", metadata.kinopoiskId)
            .put("mediaKind", metadata.mediaKind.name)
        check(preferences.edit().putString(KEY_VALUE, root.toString()).commit()) {
            "Unable to persist safe bridge metadata"
        }
    }

    fun consume(targetPackage: String, nowMs: Long): PlaybackMetadata? {
        val raw = preferences.getString(KEY_VALUE, null) ?: return null
        val value = runCatching { decode(raw) }.getOrNull()
        if (
            value == null ||
            value.targetPackage != targetPackage ||
            nowMs - value.createdAtMs !in 0..MAX_AGE_MS
        ) {
            preferences.edit().remove(KEY_VALUE).commit()
            return null
        }
        preferences.edit().remove(KEY_VALUE).commit()
        return value.metadata
    }

    private fun decode(raw: String): BridgeMetadata {
        val root = JSONObject(raw)
        return BridgeMetadata(
            targetPackage = root.getString("targetPackage"),
            createdAtMs = root.getLong("createdAtMs"),
            metadata = PlaybackMetadata(
                title = root.stringOrNull("title"),
                subtitle = root.stringOrNull("subtitle"),
                originalTitle = root.stringOrNull("originalTitle"),
                year = root.intOrNull("year"),
                season = root.intOrNull("season"),
                episode = root.intOrNull("episode"),
                imdbId = root.stringOrNull("imdbId"),
                kinopoiskId = root.stringOrNull("kinopoiskId"),
                mediaKind = SourceMediaKind.valueOf(root.getString("mediaKind")),
            ),
        )
    }

    private fun hasUsefulMetadata(metadata: PlaybackMetadata): Boolean =
        metadata.title != null ||
            metadata.originalTitle != null ||
            metadata.imdbId != null ||
            metadata.kinopoiskId != null

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private companion object {
        const val PREFERENCES_NAME = "watchrelay_bridge_metadata"
        const val KEY_VALUE = "pending"
        const val MAX_AGE_MS = 5 * 60 * 1_000L
    }
}
