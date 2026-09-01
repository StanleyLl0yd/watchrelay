package com.sl.watchrelay.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataNormalizerTest {
    @Test
    fun parsesStandardSeasonEpisodeAndStripsReleaseNoise() {
        val result = MetadataNormalizer.normalize(
            PlaybackMetadata(title = "Fallout.S02E03.1080p.WEB-DL.mkv"),
        )

        assertEquals(SourceMediaKind.EPISODE, result.mediaKind)
        assertEquals("Fallout", result.title)
        assertEquals("fallout", result.titleKey)
        assertEquals(2, result.season)
        assertEquals(3, result.episode)
    }

    @Test
    fun parsesAlternativeSeasonEpisodeNotation() {
        val result = MetadataNormalizer.normalize(
            PlaybackMetadata(title = "Severance 2x05 2160p HEVC"),
        )

        assertEquals(SourceMediaKind.EPISODE, result.mediaKind)
        assertEquals("Severance", result.title)
        assertEquals(2, result.season)
        assertEquals(5, result.episode)
    }

    @Test
    fun extractsMovieYearWithoutTreatingResolutionAsYear() {
        val result = MetadataNormalizer.normalize(
            PlaybackMetadata(
                title = "Dune.Part.Two.2024.2160p.WEB-DL.mkv",
                mediaKind = SourceMediaKind.MOVIE,
            ),
        )

        assertEquals("Dune Part Two", result.title)
        assertEquals(2024, result.year)
        assertNull(result.season)
        assertNull(result.episode)
    }

    @Test
    fun explicitMetadataWinsOverParsedFilenameValues() {
        val result = MetadataNormalizer.normalize(
            PlaybackMetadata(
                title = "Show.S01E01.2020.mkv",
                year = 2021,
                season = 2,
                episode = 7,
            ),
        )

        assertEquals(2021, result.year)
        assertEquals(2, result.season)
        assertEquals(7, result.episode)
    }

    @Test
    fun comparisonKeyNormalizesPunctuationCaseAndDiacritics() {
        assertEquals("amelie le destin", MetadataNormalizer.comparisonKey("Amélie: Le Destin"))
    }
}
