package com.sl.watchrelay.myshows

import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class MovieState(
    val title: String?,
    val watchStatus: String?,
)

class MyShowsHttpException(
    val statusCode: Int,
) : IOException("MyShows returned HTTP $statusCode")

class MyShowsApiException(
    message: String,
) : IOException(message)

class MyShowsFreeClient {
    fun authenticate(login: String, password: String): Result<String> = runCatching {
        require(login.isNotBlank()) { "Login is required" }
        require(password.isNotEmpty()) { "Password is required" }
        val response = post(
            SESSION_URL,
            JSONObject()
                .put("login", login)
                .put("password", password),
        )
        response.optJSONObject("error")?.let {
            throw MyShowsApiException(it.optString("message", "Authentication failed"))
        }
        response.optString("token").takeIf(String::isNotBlank)
            ?: throw MyShowsApiException("MyShows did not return a session token")
    }

    fun readMovieState(token: String, movieId: Int): Result<MovieState> = runCatching {
        val result = rpcObject(
            token = token,
            method = "shows.GetById",
            params = JSONObject()
                .put("showId", movieId)
                .put("withEpisodes", false)
                .put("withSeasonCounts", false),
        )
        MovieState(
            title = result.optString("title").takeIf(String::isNotBlank),
            watchStatus = result.optString("watchStatus").takeIf(String::isNotBlank),
        )
    }

    fun setMovieStatus(token: String, movieId: Int, status: String): Result<Unit> = runCatching {
        require(status in MOVIE_STATUSES) { "Unsupported movie status: $status" }
        rpc(
            token = token,
            method = "manage.SetShowStatus",
            params = JSONObject().put("id", movieId).put("status", status),
        )
        Unit
    }

    fun checkEpisode(token: String, episodeId: Int): Result<Unit> = runCatching {
        rpc(
            token = token,
            method = "manage.CheckEpisode",
            params = JSONObject().put("id", episodeId).put("rating", 0),
        )
        Unit
    }

    fun uncheckEpisode(token: String, episodeId: Int): Result<Unit> = runCatching {
        rpc(
            token = token,
            method = "manage.UnCheckEpisode",
            params = JSONObject().put("id", episodeId).put("rating", 0),
        )
        Unit
    }

    private fun rpcObject(token: String, method: String, params: JSONObject): JSONObject {
        val result = rpc(token, method, params)
        return result as? JSONObject ?: throw MyShowsApiException("Unexpected MyShows response for $method")
    }

    private fun rpc(token: String, method: String, params: JSONObject): Any? {
        require(token.isNotBlank()) { "Not authenticated" }
        val response = post(
            RPC_URL,
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("params", params)
                .put("id", 1),
            mapOf(AUTH_HEADER to "Bearer $token"),
        )
        response.optJSONObject("error")?.let {
            throw MyShowsApiException(it.optString("message", "MyShows request failed"))
        }
        if (!response.has("result")) {
            throw MyShowsApiException("MyShows response has no result")
        }
        return response.opt("result")
    }

    private fun post(
        endpoint: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw MyShowsHttpException(status)
            if (text.isBlank()) throw MyShowsApiException("MyShows returned an empty response")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SESSION_URL = "https://myshows.me/api/session"
        const val RPC_URL = "https://myshows.me/v3/rpc/"
        const val AUTH_HEADER = "authorization2"
        const val TIMEOUT_MS = 15_000
        val MOVIE_STATUSES = setOf("finished", "later", "remove")
    }
}
