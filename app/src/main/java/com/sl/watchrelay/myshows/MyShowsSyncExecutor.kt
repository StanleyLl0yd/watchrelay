package com.sl.watchrelay.myshows

import com.sl.watchrelay.security.TrackerTokenStore
import com.sl.watchrelay.sync.PendingMutation
import com.sl.watchrelay.sync.SyncExecutionResult
import com.sl.watchrelay.sync.SyncExecutor
import com.sl.watchrelay.sync.SyncOperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class MyShowsSyncExecutor(
    private val client: MyShowsFreeClient,
    private val tokenStore: TrackerTokenStore,
) : SyncExecutor {
    override suspend fun execute(mutation: PendingMutation): SyncExecutionResult = withContext(Dispatchers.IO) {
        val token = tokenStore.read()
            ?: return@withContext SyncExecutionResult.AuthenticationRequired("MyShows authentication is required")

        val result = when (mutation.type) {
            SyncOperationType.CHECK_EPISODE -> client.checkEpisode(token, mutation.remoteId)
            SyncOperationType.UNCHECK_EPISODE -> client.uncheckEpisode(token, mutation.remoteId)
            SyncOperationType.SET_MOVIE_STATUS -> {
                val status = mutation.value
                    ?: return@withContext SyncExecutionResult.PermanentFailure("Movie status is missing")
                client.setMovieStatus(token, mutation.remoteId, status)
            }
        }

        result.fold(
            onSuccess = { SyncExecutionResult.Success },
            onFailure = { classifyFailure(it) },
        )
    }

    private fun classifyFailure(error: Throwable): SyncExecutionResult = when (error) {
        is MyShowsHttpException -> when (error.statusCode) {
            401, 403 -> {
                tokenStore.clear()
                SyncExecutionResult.AuthenticationRequired(error.message.orEmpty())
            }
            408, 425, 429 -> SyncExecutionResult.RetryableFailure(error.message.orEmpty())
            in 500..599 -> SyncExecutionResult.RetryableFailure(error.message.orEmpty())
            else -> SyncExecutionResult.PermanentFailure(error.message.orEmpty())
        }
        is MyShowsApiException -> SyncExecutionResult.PermanentFailure(error.message.orEmpty())
        is IOException -> SyncExecutionResult.RetryableFailure(error.message ?: "MyShows network request failed")
        else -> SyncExecutionResult.PermanentFailure(error.message ?: error.javaClass.simpleName)
    }
}
