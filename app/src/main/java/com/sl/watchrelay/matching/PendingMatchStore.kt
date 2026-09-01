package com.sl.watchrelay.matching

import android.content.Context
import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.TrackerProvider
import org.json.JSONArray
import org.json.JSONObject

data class PendingMatch(
    val eventId: String,
    val itemKey: String,
    val viewedMs: Long,
    val durationMs: Long,
    val watchedAtMs: Long,
    val metadata: PlaybackMetadata,
    val candidates: List<ContentMatchCandidate>,
    val reason: String,
) {
    init {
        require(eventId.isNotBlank())
        require(itemKey.isNotBlank())
        require(viewedMs >= 0)
        require(durationMs > 0)
        require(viewedMs <= durationMs)
        require(watchedAtMs >= 0)
        require(candidates.isNotEmpty())
    }
}

interface PendingMatchStore {
    suspend fun put(match: PendingMatch)
    suspend fun get(eventId: String): PendingMatch?
    suspend fun remove(eventId: String)
    suspend fun list(): List<PendingMatch>
}

class SharedPreferencesPendingMatchStore(
    context: Context,
) : PendingMatchStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override suspend fun put(match: PendingMatch) {
        check(
            preferences.edit()
                .putString(key(match.eventId), PendingMatchCodec.encode(match))
                .commit(),
        ) { "Unable to persist pending content match" }
    }

    override suspend fun get(eventId: String): PendingMatch? = preferences
        .getString(key(eventId), null)
        ?.let(PendingMatchCodec::decode)

    override suspend fun remove(eventId: String) {
        preferences.edit().remove(key(eventId)).commit()
    }

    override suspend fun list(): List<PendingMatch> = preferences.all
        .asSequence()
        .filter { (key, _) -> key.startsWith(KEY_PREFIX) }
        .mapNotNull { (_, value) -> (value as? String)?.let(PendingMatchCodec::decode) }
        .sortedByDescending(PendingMatch::watchedAtMs)
        .toList()

    private fun key(eventId: String) = "$KEY_PREFIX$eventId"

    private companion object {
        const val PREFERENCES_NAME = "watchrelay_pending_matches"
        const val KEY_PREFIX = "match:"
    }
}

object PendingMatchCodec {
    fun encode(match: PendingMatch): String = JSONObject()
        .put("eventId", match.eventId)
        .put("itemKey", match.itemKey)
        .put("viewedMs", match.viewedMs)
        .put("durationMs", match.durationMs)
        .put("watchedAtMs", match.watchedAtMs)
        .put("metadata", encodeMetadata(match.metadata))
        .put("reason", match.reason)
        .put(
            "candidates",
            JSONArray().apply {
                match.candidates.forEach { put(encodeCandidate(it)) }
            },
        )
        .toString()

    fun decode(value: String): PendingMatch? = runCatching {
        val root = JSONObject(value)
        val candidates = root.getJSONArray("candidates")
        PendingMatch(
            eventId = root.getString("eventId"),
            itemKey = root.getString("itemKey"),
            viewedMs = root.getLong("viewedMs"),
            durationMs = root.getLong("durationMs"),
            watchedAtMs = root.getLong("watchedAtMs"),
            metadata = decodeMetadata(root.getJSONObject("metadata")),
            candidates = buildList {
                for (index in 0 until candidates.length()) {
                    add(decodeCandidate(candidates.getJSONObject(index)))
                }
            },
            reason = root.getString("reason"),
        )
    }.getOrNull()

    private fun encodeMetadata(metadata: PlaybackMetadata) = JSONObject()
        .putNullable("title", metadata.title)
        .putNullable("subtitle", metadata.subtitle)
        .putNullable("originalTitle", metadata.originalTitle)
        .putNullable("year", metadata.year)
        .putNullable("season", metadata.season)
        .putNullable("episode", metadata.episode)
        .putNullable("imdbId", metadata.imdbId)
        .putNullable("kinopoiskId", metadata.kinopoiskId)
        .put("mediaKind", metadata.mediaKind.name)

    private fun decodeMetadata(value: JSONObject) = PlaybackMetadata(
        title = value.stringOrNull("title"),
        subtitle = value.stringOrNull("subtitle"),
        originalTitle = value.stringOrNull("originalTitle"),
        year = value.intOrNull("year"),
        season = value.intOrNull("season"),
        episode = value.intOrNull("episode"),
        imdbId = value.stringOrNull("imdbId"),
        kinopoiskId = value.stringOrNull("kinopoiskId"),
        mediaKind = SourceMediaKind.valueOf(value.getString("mediaKind")),
    )

    private fun encodeCandidate(candidate: ContentMatchCandidate) = JSONObject()
        .put("provider", candidate.mapping.provider.name)
        .put("mediaType", candidate.mapping.mediaType.name)
        .put("remoteId", candidate.mapping.remoteId)
        .putNullable("showId", candidate.mapping.showId)
        .putNullable("title", candidate.title)
        .putNullable("originalTitle", candidate.originalTitle)
        .putNullable("year", candidate.year)
        .putNullable("season", candidate.season)
        .putNullable("episode", candidate.episode)
        .put("confidence", candidate.confidence)

    private fun decodeCandidate(value: JSONObject) = ContentMatchCandidate(
        mapping = SavedContentMapping(
            provider = TrackerProvider.valueOf(value.getString("provider")),
            mediaType = RemoteMediaType.valueOf(value.getString("mediaType")),
            remoteId = value.getInt("remoteId"),
            showId = value.intOrNull("showId"),
        ),
        title = value.stringOrNull("title"),
        originalTitle = value.stringOrNull("originalTitle"),
        year = value.intOrNull("year"),
        season = value.intOrNull("season"),
        episode = value.intOrNull("episode"),
        confidence = value.getInt("confidence"),
    )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)
}
