package com.sl.watchrelay.myshows

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyShowsResponseParserTest {
    @Test
    fun parsesShowSearchArrayFixture() {
        val result = JSONArray().put(
            JSONObject()
                .put("id", 20)
                .put("title", "Fallout")
                .put("titleOriginal", "Fallout")
                .put("year", 2024)
                .put("imdbId", "tt12637874")
                .put("kinopoiskId", "1048334"),
        )

        val item = MyShowsResponseParser.catalogItems(result).single()

        assertEquals(20, item.id)
        assertEquals("Fallout", item.title)
        assertEquals("Fallout", item.originalTitle)
        assertEquals(2024, item.year)
        assertEquals("tt12637874", item.imdbId)
        assertEquals("1048334", item.kinopoiskId)
    }

    @Test
    fun parsesMovieCatalogWrapperFixture() {
        val result = JSONObject().put(
            "shows",
            JSONArray().put(
                JSONObject()
                    .put("id", 10)
                    .put("title", "Dune")
                    .put("originalTitle", "Dune")
                    .put("year", 2021),
            ),
        )

        val item = MyShowsResponseParser.catalogItems(result, "shows", "movies").single()

        assertEquals(10, item.id)
        assertEquals("Dune", item.title)
        assertEquals(2021, item.year)
    }

    @Test
    fun parsesEpisodeCoordinatesFixture() {
        val result = JSONObject().put(
            "episodes",
            JSONArray().put(
                JSONObject()
                    .put("episodeId", 203)
                    .put("seasonNumber", 2)
                    .put("episodeNumber", 3)
                    .put("title", "Episode 3"),
            ),
        )

        val episode = MyShowsResponseParser.episodes(showId = 20, result = result).single()

        assertEquals(20, episode.showId)
        assertEquals(203, episode.episodeId)
        assertEquals(2, episode.seasonNumber)
        assertEquals(3, episode.episodeNumber)
    }

    @Test
    fun profileEpisodesFixtureContainsOnlyWatchedIds() {
        val result = JSONArray()
            .put(JSONObject().put("episodeId", 201))
            .put(JSONObject().put("id", 203))

        val watched = MyShowsResponseParser.watchedEpisodeIds(result)

        assertEquals(setOf(201, 203), watched)
        assertTrue(202 !in watched)
    }

    @Test
    fun malformedItemsAreSkippedInsteadOfInventingIds() {
        val result = JSONArray()
            .put(JSONObject().put("title", "Missing id"))
            .put(JSONObject().put("id", 0).put("title", "Invalid id"))

        assertTrue(MyShowsResponseParser.catalogItems(result).isEmpty())
    }
}
