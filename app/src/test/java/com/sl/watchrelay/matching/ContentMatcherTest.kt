package com.sl.watchrelay.matching

import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.SyncMutation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentMatcherTest {
    @Test
    fun exactMovieTitleAndYearAutoConfirmAndCapturePreviousState() = runBlocking {
        val catalog = FakeCatalog().apply {
            movies += CatalogItem(10, "Dune", "Dune", 2021)
            movieStates[10] = "later"
        }
        val result = ContentMatcher(catalog, MemoryMappingStore()).resolve(
            PlaybackMetadata(title = "Dune", year = 2021, mediaKind = SourceMediaKind.MOVIE),
        ) as ContentMatchResult.Confirmed

        assertEquals(10, result.content.target.remoteId)
        assertEquals(RemoteMediaType.MOVIE, result.content.target.mediaType)
        assertEquals("later", result.content.target.previousRemoteState)
        assertEquals(100, result.confidence)
        assertEquals(MatchEvidence.TITLE_AND_YEAR, result.evidence)
    }

    @Test
    fun titleOnlyMovieDoesNotAutoConfirm() = runBlocking {
        val catalog = FakeCatalog().apply {
            movies += CatalogItem(10, "Dune", "Dune", 2021)
        }
        val result = ContentMatcher(catalog, MemoryMappingStore()).resolve(
            PlaybackMetadata(title = "Dune", mediaKind = SourceMediaKind.MOVIE),
        )

        assertTrue(result is ContentMatchResult.Ambiguous)
        assertEquals(80, (result as ContentMatchResult.Ambiguous).candidates.single().confidence)
    }

    @Test
    fun equalMovieCandidatesRemainAmbiguous() = runBlocking {
        val catalog = FakeCatalog().apply {
            movies += CatalogItem(10, "The Office", "The Office", 2005)
            movies += CatalogItem(11, "The Office", "The Office", 2005)
        }
        val result = ContentMatcher(catalog, MemoryMappingStore()).resolve(
            PlaybackMetadata(title = "The Office", year = 2005, mediaKind = SourceMediaKind.MOVIE),
        )

        assertTrue(result is ContentMatchResult.Ambiguous)
        assertEquals(2, (result as ContentMatchResult.Ambiguous).candidates.size)
    }

    @Test
    fun exactShowAndEpisodeAutoConfirm() = runBlocking {
        val catalog = FakeCatalog().apply {
            shows += CatalogItem(20, "Fallout", "Fallout", 2024)
            showEpisodes[20] = listOf(CatalogEpisode(20, 203, 2, 3, "Episode 3"))
            episodeStates[203] = SyncMutation.EPISODE_WATCHED_STATE
        }
        val result = ContentMatcher(catalog, MemoryMappingStore()).resolve(
            PlaybackMetadata(title = "Fallout.S02E03.1080p.mkv", year = 2024),
        ) as ContentMatchResult.Confirmed

        assertEquals(203, result.content.target.remoteId)
        assertEquals(RemoteMediaType.EPISODE, result.content.target.mediaType)
        assertEquals(SyncMutation.EPISODE_WATCHED_STATE, result.content.target.previousRemoteState)
        assertEquals(2, result.content.season)
        assertEquals(3, result.content.episode)
    }

    @Test
    fun externalIdTakesPrecedenceOverTitle() = runBlocking {
        val catalog = FakeCatalog().apply {
            external["imdb:tt1234567"] = CatalogItem(31, "Correct Movie", "Correct Movie", 2024)
            movieStates[31] = null
        }
        val result = ContentMatcher(catalog, MemoryMappingStore()).resolve(
            PlaybackMetadata(
                title = "Completely Wrong Local Label",
                year = 2024,
                imdbId = "tt1234567",
                mediaKind = SourceMediaKind.MOVIE,
            ),
        ) as ContentMatchResult.Confirmed

        assertEquals(31, result.content.target.remoteId)
        assertEquals(MatchEvidence.EXTERNAL_ID, result.evidence)
        assertEquals(100, result.confidence)
    }

    @Test
    fun unknownMediaTypeDoesNotAutoMatchMovie() = runBlocking {
        val catalog = FakeCatalog().apply {
            movies += CatalogItem(10, "Dune", "Dune", 2021)
        }
        val result = ContentMatcher(catalog, MemoryMappingStore()).resolve(
            PlaybackMetadata(title = "Dune", year = 2021),
        )

        assertTrue(result is ContentMatchResult.Unresolved)
    }

    @Test
    fun episodeWithoutCoordinatesDoesNotSync() = runBlocking {
        val result = ContentMatcher(FakeCatalog(), MemoryMappingStore()).resolve(
            PlaybackMetadata(title = "Fallout", mediaKind = SourceMediaKind.EPISODE),
        )

        assertTrue(result is ContentMatchResult.Unresolved)
    }

    @Test
    fun confirmedCorrectionPersistsAndRefreshesRemoteState() = runBlocking {
        val catalog = FakeCatalog().apply {
            movies += CatalogItem(10, "The Office", "The Office", 2005)
            movies += CatalogItem(11, "The Office", "The Office", 2005)
            movieStates[11] = "later"
        }
        val mappings = MemoryMappingStore()
        val matcher = ContentMatcher(catalog, mappings)
        val source = PlaybackMetadata(title = "The Office", year = 2005, mediaKind = SourceMediaKind.MOVIE)
        val ambiguous = matcher.resolve(source) as ContentMatchResult.Ambiguous
        val selected = ambiguous.candidates.single { it.mapping.remoteId == 11 }

        val confirmed = matcher.confirm(source, selected)
        assertEquals("later", confirmed.content.target.previousRemoteState)

        catalog.movies.clear()
        catalog.movieStates[11] = "finished"
        val reused = matcher.resolve(source) as ContentMatchResult.Confirmed

        assertEquals(11, reused.content.target.remoteId)
        assertEquals("finished", reused.content.target.previousRemoteState)
        assertEquals(MatchEvidence.USER_MAPPING, reused.evidence)
    }

    @Test
    fun ambiguousShowCandidatesBecomeExactEpisodeChoices() = runBlocking {
        val catalog = FakeCatalog().apply {
            shows += CatalogItem(20, "The Office", "The Office", 2005)
            shows += CatalogItem(21, "The Office", "The Office", 2005)
            showEpisodes[20] = listOf(CatalogEpisode(20, 201, 2, 3))
            showEpisodes[21] = listOf(CatalogEpisode(21, 211, 2, 3))
        }
        val result = ContentMatcher(catalog, MemoryMappingStore()).resolve(
            PlaybackMetadata(title = "The Office S02E03", year = 2005),
        ) as ContentMatchResult.Ambiguous

        assertEquals(setOf(201, 211), result.candidates.map { it.mapping.remoteId }.toSet())
        assertTrue(result.candidates.all { it.mapping.showId != null })
    }

    private class MemoryMappingStore : ContentMappingStore {
        private val values = mutableMapOf<String, SavedContentMapping>()

        override suspend fun get(signature: String): SavedContentMapping? = values[signature]

        override suspend fun put(signature: String, mapping: SavedContentMapping) {
            values[signature] = mapping
        }

        override suspend fun remove(signature: String) {
            values.remove(signature)
        }
    }

    private class FakeCatalog : ContentCatalog {
        val shows = mutableListOf<CatalogItem>()
        val movies = mutableListOf<CatalogItem>()
        val external = mutableMapOf<String, CatalogItem>()
        val showEpisodes = mutableMapOf<Int, List<CatalogEpisode>>()
        val movieStates = mutableMapOf<Int, String?>()
        val episodeStates = mutableMapOf<Int, String?>()

        override suspend fun findByExternalId(source: String, id: String): CatalogItem? = external["$source:$id"]

        override suspend fun searchShows(query: String): List<CatalogItem> = shows

        override suspend fun searchMovies(query: String, year: Int?): List<CatalogItem> = movies

        override suspend fun episodes(showId: Int): List<CatalogEpisode> = showEpisodes[showId].orEmpty()

        override suspend fun previousMovieState(movieId: Int): String? = movieStates[movieId]

        override suspend fun previousEpisodeState(showId: Int, episodeId: Int): String? =
            episodeStates[episodeId] ?: SyncMutation.EPISODE_UNWATCHED_STATE
    }
}
