package com.sl.watchrelay.matching

import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.TrackerProvider
import com.sl.watchrelay.sync.TrackerTarget

enum class SourceMediaKind {
    UNKNOWN,
    MOVIE,
    EPISODE,
}

data class PlaybackMetadata(
    val title: String?,
    val subtitle: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val imdbId: String? = null,
    val kinopoiskId: String? = null,
    val mediaKind: SourceMediaKind = SourceMediaKind.UNKNOWN,
)

data class NormalizedMetadata(
    val title: String?,
    val titleKey: String?,
    val originalTitle: String?,
    val originalTitleKey: String?,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val imdbId: String?,
    val kinopoiskId: String?,
    val mediaKind: SourceMediaKind,
) {
    val mappingSignature: String
        get() = listOf(
            mediaKind.name,
            originalTitleKey.orEmpty(),
            titleKey.orEmpty(),
            year?.toString().orEmpty(),
            season?.toString().orEmpty(),
            episode?.toString().orEmpty(),
            imdbId.orEmpty(),
            kinopoiskId.orEmpty(),
        ).joinToString("|")
}

data class CatalogItem(
    val id: Int,
    val title: String?,
    val originalTitle: String?,
    val year: Int?,
    val imdbId: String? = null,
    val kinopoiskId: String? = null,
)

data class CatalogEpisode(
    val showId: Int,
    val episodeId: Int,
    val season: Int,
    val episode: Int,
    val title: String? = null,
)

data class SavedContentMapping(
    val provider: TrackerProvider,
    val mediaType: RemoteMediaType,
    val remoteId: Int,
    val showId: Int? = null,
) {
    init {
        require(remoteId > 0)
        if (mediaType == RemoteMediaType.EPISODE) requireNotNull(showId) { "Episode mapping requires showId" }
    }
}

data class ContentMatchCandidate(
    val mapping: SavedContentMapping,
    val title: String?,
    val originalTitle: String?,
    val year: Int?,
    val season: Int? = null,
    val episode: Int? = null,
    val confidence: Int,
)

enum class MatchEvidence {
    USER_MAPPING,
    EXTERNAL_ID,
    TITLE_AND_YEAR,
}

data class ResolvedContent(
    val target: TrackerTarget,
    val title: String?,
    val originalTitle: String?,
    val year: Int?,
    val season: Int? = null,
    val episode: Int? = null,
)

sealed interface ContentMatchResult {
    data class Confirmed(
        val content: ResolvedContent,
        val confidence: Int,
        val evidence: MatchEvidence,
    ) : ContentMatchResult

    data class Ambiguous(
        val candidates: List<ContentMatchCandidate>,
        val reason: String,
    ) : ContentMatchResult

    data class Unresolved(
        val reason: String,
    ) : ContentMatchResult
}

interface ContentCatalog {
    suspend fun findByExternalId(source: String, id: String): CatalogItem?
    suspend fun searchShows(query: String): List<CatalogItem>
    suspend fun searchMovies(query: String, year: Int?): List<CatalogItem>
    suspend fun episodes(showId: Int): List<CatalogEpisode>
    suspend fun previousMovieState(movieId: Int): String?
    suspend fun previousEpisodeState(showId: Int, episodeId: Int): String?
}

interface ContentMappingStore {
    suspend fun get(signature: String): SavedContentMapping?
    suspend fun put(signature: String, mapping: SavedContentMapping)
    suspend fun remove(signature: String)
}
