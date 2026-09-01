package com.sl.watchrelay.myshows

import org.json.JSONArray
import org.json.JSONObject

internal object MyShowsResponseParser {
    fun catalogItems(result: Any?, vararg collectionKeys: String): List<MyShowsCatalogItem> =
        extractArray(result, *collectionKeys).let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toCatalogItem()?.let(::add)
                }
            }
        }

    fun catalogItem(result: Any?): MyShowsCatalogItem? =
        (result as? JSONObject)?.toCatalogItem()

    fun episodes(showId: Int, result: Any?): List<MyShowsEpisode> {
        val objectResult = result as? JSONObject ?: return emptyList()
        val array = objectResult.optJSONArray("episodes") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val episodeId = item.positiveInt("episodeId", "id") ?: continue
                val season = item.nonNegativeInt("seasonNumber", "season") ?: continue
                val episode = item.positiveInt("episodeNumber", "episode") ?: continue
                add(
                    MyShowsEpisode(
                        showId = showId,
                        episodeId = episodeId,
                        seasonNumber = season,
                        episodeNumber = episode,
                        title = item.stringOrNull("title"),
                    ),
                )
            }
        }
    }

    fun watchedEpisodeIds(result: Any?): Set<Int> = buildSet {
        val array = extractArray(result, "episodes", "results", "items", "list")
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            item.positiveInt("episodeId", "id")?.let(::add)
        }
    }

    fun extractArray(result: Any?, vararg keys: String): JSONArray = when (result) {
        is JSONArray -> result
        is JSONObject -> keys.firstNotNullOfOrNull { result.optJSONArray(it) } ?: JSONArray()
        else -> JSONArray()
    }

    private fun JSONObject.toCatalogItem(): MyShowsCatalogItem? {
        val id = positiveInt("id", "showId", "movieId") ?: return null
        return MyShowsCatalogItem(
            id = id,
            title = stringOrNull("title", "name"),
            originalTitle = stringOrNull("titleOriginal", "originalTitle", "original_name", "original_title"),
            year = positiveInt("year", "releaseYear"),
            imdbId = stringOrNull("imdbId", "imdb_id"),
            kinopoiskId = stringOrNull("kinopoiskId", "kinopoisk_id"),
        )
    }

    private fun JSONObject.stringOrNull(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.positiveInt(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is Number -> value.toInt().takeIf { it > 0 }
            is String -> value.toIntOrNull()?.takeIf { it > 0 }
            else -> null
        }
    }

    private fun JSONObject.nonNegativeInt(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is Number -> value.toInt().takeIf { it >= 0 }
            is String -> value.toIntOrNull()?.takeIf { it >= 0 }
            else -> null
        }
    }
}
