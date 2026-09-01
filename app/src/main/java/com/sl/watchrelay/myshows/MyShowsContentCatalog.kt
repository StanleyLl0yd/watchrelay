package com.sl.watchrelay.myshows

import com.sl.watchrelay.matching.CatalogEpisode
import com.sl.watchrelay.matching.CatalogItem
import com.sl.watchrelay.matching.ContentCatalog
import com.sl.watchrelay.security.TrackerTokenStore
import com.sl.watchrelay.sync.SyncMutation

class MyShowsContentCatalog(
    private val client: MyShowsFreeClient,
    private val tokenStore: TrackerTokenStore,
) : ContentCatalog {
    override suspend fun findByExternalId(source: String, id: String): CatalogItem? =
        client.findByExternalId(source, id).getOrThrow()?.toDomain()

    override suspend fun searchShows(query: String): List<CatalogItem> =
        client.searchShows(query).getOrThrow().map { it.toDomain() }

    override suspend fun searchMovies(query: String, year: Int?): List<CatalogItem> =
        client.searchMovies(query, year).getOrThrow().map { it.toDomain() }

    override suspend fun episodes(showId: Int): List<CatalogEpisode> =
        client.readShowEpisodes(showId).getOrThrow().map { episode ->
            CatalogEpisode(
                showId = showId,
                episodeId = episode.episodeId,
                season = episode.seasonNumber,
                episode = episode.episodeNumber,
                title = episode.title,
            )
        }

    override suspend fun previousMovieState(movieId: Int): String? {
        val token = requireToken()
        return client.readMovieState(token, movieId).getOrThrow().watchStatus
    }

    override suspend fun previousEpisodeState(showId: Int, episodeId: Int): String {
        val token = requireToken()
        val watched = client.readProfileWatchedEpisodeIds(token, showId).getOrThrow()
        return if (episodeId in watched) {
            SyncMutation.EPISODE_WATCHED_STATE
        } else {
            SyncMutation.EPISODE_UNWATCHED_STATE
        }
    }

    private fun requireToken(): String = tokenStore.read()
        ?: throw MyShowsApiException("MyShows authentication is required to capture previous state")

    private fun MyShowsCatalogItem.toDomain() = CatalogItem(
        id = id,
        title = title,
        originalTitle = originalTitle,
        year = year,
        imdbId = imdbId,
        kinopoiskId = kinopoiskId,
    )
}
