package com.sl.watchrelay.playback

import com.sl.watchrelay.matching.SourceMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentPlaybackMetadataExtractorTest {
    @Test
    fun extractsAllowlistedMovieMetadata() {
        val metadata = IntentPlaybackMetadataExtractor.extract(
            mapOf(
                "movie_title" to "Dune",
                "original_title" to "Dune",
                "release_year" to 2021,
                "imdb_id" to "tt1160419",
                "content_type" to "movie",
                "stream_url" to "https://example.invalid/video",
            ),
        )

        assertEquals("Dune", metadata.title)
        assertEquals(2021, metadata.year)
        assertEquals("tt1160419", metadata.imdbId)
        assertEquals(SourceMediaKind.MOVIE, metadata.mediaKind)
    }

    @Test
    fun extractsEpisodeCoordinates() {
        val metadata = IntentPlaybackMetadataExtractor.extract(
            mapOf(
                "title" to "Fallout",
                "season_number" to "2",
                "episode_number" to 3,
                "type" to "episode",
            ),
        )

        assertEquals(2, metadata.season)
        assertEquals(3, metadata.episode)
        assertEquals(SourceMediaKind.EPISODE, metadata.mediaKind)
    }

    @Test
    fun urlLookingValueIsNeverAcceptedAsMetadata() {
        val metadata = IntentPlaybackMetadataExtractor.extract(
            mapOf(
                "title" to "https://example.invalid/private",
                "imdb_id" to "content://private/id",
                "year" to 2024,
            ),
        )

        assertNull(metadata.title)
        assertNull(metadata.imdbId)
        assertEquals(2024, metadata.year)
    }
}
