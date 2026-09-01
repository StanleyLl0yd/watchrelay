package com.sl.watchrelay.ui

import android.content.Context
import com.sl.watchrelay.matching.CompletedPlaybackDecision
import com.sl.watchrelay.matching.CompletedWatchResolution
import com.sl.watchrelay.matching.CompletedWatchResolver
import com.sl.watchrelay.matching.ContentMatchCandidate
import com.sl.watchrelay.matching.PendingMatch
import com.sl.watchrelay.matching.PendingMatchState
import com.sl.watchrelay.myshows.MyShowsFreeClient
import com.sl.watchrelay.playback.ExternalPlayerPreferences
import com.sl.watchrelay.security.KeystoreTokenStore
import com.sl.watchrelay.settings.AppSettings
import com.sl.watchrelay.storage.PendingSyncEntity
import com.sl.watchrelay.storage.WatchHistoryEntity
import com.sl.watchrelay.storage.WatchRelayDatabase
import com.sl.watchrelay.sync.HistorySyncState
import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.SyncWorkScheduler
import com.sl.watchrelay.sync.WatchSyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MvpHistoryItem(
    val eventId: String,
    val itemKey: String,
    val viewedPercent: Int,
    val watchedAtMs: Long,
    val remoteType: RemoteMediaType,
    val remoteId: Int,
    val syncState: HistorySyncState,
    val canUndo: Boolean,
)

data class MvpAttentionItem(
    val eventId: String,
    val state: String,
    val attempts: Int,
    val message: String?,
)

data class MvpMatchCandidate(
    val title: String?,
    val originalTitle: String?,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val confidence: Int,
)

data class MvpMatchAttentionItem(
    val eventId: String,
    val itemKey: String,
    val watchedAtMs: Long,
    val state: PendingMatchState,
    val reason: String,
    val candidates: List<MvpMatchCandidate>,
)

data class MvpSnapshot(
    val authenticated: Boolean,
    val pendingCount: Int,
    val authRequiredCount: Int,
    val failedCount: Int,
    val watchedThresholdPercent: Int,
    val onboardingCompleted: Boolean,
    val externalPlayerPackage: String?,
    val history: List<MvpHistoryItem>,
    val attention: List<MvpAttentionItem>,
    val matchAttention: List<MvpMatchAttentionItem>,
)

class MvpRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dao = WatchRelayDatabase.get(appContext).watchStoreDao()
    private val tokenStore = KeystoreTokenStore(appContext)
    private val settings = AppSettings(appContext)
    private val playerPreferences = ExternalPlayerPreferences(appContext)
    private val syncCoordinator = WatchSyncCoordinator(appContext)
    private val completedWatchResolver = CompletedWatchResolver(appContext)
    private val myShows = MyShowsFreeClient()

    suspend fun snapshot(): MvpSnapshot = withContext(Dispatchers.IO) {
        MvpSnapshot(
            authenticated = tokenStore.read() != null,
            pendingCount = dao.pendingCount(),
            authRequiredCount = dao.authRequiredCount(),
            failedCount = dao.failedCount(),
            watchedThresholdPercent = settings.watchedThresholdPercent,
            onboardingCompleted = settings.onboardingCompleted,
            externalPlayerPackage = playerPreferences.targetPackage,
            history = dao.recentHistory(HISTORY_LIMIT).map { it.toMvp() },
            attention = dao.attentionItems(ATTENTION_LIMIT).map { it.toMvp() },
            matchAttention = completedWatchResolver.pending().map { it.toMvp() },
        )
    }

    suspend fun authenticate(login: String, password: String) = withContext(Dispatchers.IO) {
        val token = myShows.authenticate(login.trim(), password).getOrThrow()
        syncCoordinator.saveMyShowsToken(token)
        settings.onboardingCompleted = true
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        syncCoordinator.clearMyShowsToken()
    }

    suspend fun undo(eventId: String): Boolean = withContext(Dispatchers.IO) {
        syncCoordinator.undo(eventId)
    }

    suspend fun syncNow() = withContext(Dispatchers.IO) {
        SyncWorkScheduler.schedule(appContext)
    }

    suspend fun setWatchedThresholdPercent(value: Int) = withContext(Dispatchers.IO) {
        settings.watchedThresholdPercent = value
    }

    suspend fun forgetExternalPlayer() = withContext(Dispatchers.IO) {
        playerPreferences.targetPackage = null
    }

    suspend fun completeOnboarding() = withContext(Dispatchers.IO) {
        settings.onboardingCompleted = true
    }

    suspend fun resolveCompletedWatch(decision: CompletedPlaybackDecision): CompletedWatchResolution =
        withContext(Dispatchers.IO) {
            completedWatchResolver.resolve(decision)
        }

    suspend fun retryMatch(eventId: String) = withContext(Dispatchers.IO) {
        completedWatchResolver.retry(eventId)
    }

    suspend fun confirmMatch(eventId: String, candidateIndex: Int) = withContext(Dispatchers.IO) {
        completedWatchResolver.confirm(eventId, candidateIndex)
    }

    suspend fun dismissMatch(eventId: String) = withContext(Dispatchers.IO) {
        completedWatchResolver.dismiss(eventId)
    }

    private fun WatchHistoryEntity.toMvp(): MvpHistoryItem {
        val state = HistorySyncState.valueOf(syncState)
        return MvpHistoryItem(
            eventId = eventId,
            itemKey = itemKey,
            viewedPercent = if (durationMs > 0) {
                ((viewedMs * 100) / durationMs).toInt().coerceIn(0, 100)
            } else {
                0
            },
            watchedAtMs = watchedAtMs,
            remoteType = RemoteMediaType.valueOf(remoteType),
            remoteId = remoteId,
            syncState = state,
            canUndo = state !in setOf(HistorySyncState.UNDONE, HistorySyncState.UNDO_PENDING),
        )
    }

    private fun PendingSyncEntity.toMvp() = MvpAttentionItem(
        eventId = eventId,
        state = state,
        attempts = attemptCount,
        message = lastError,
    )

    private fun PendingMatch.toMvp() = MvpMatchAttentionItem(
        eventId = eventId,
        itemKey = itemKey,
        watchedAtMs = watchedAtMs,
        state = state,
        reason = reason,
        candidates = candidates.map { it.toMvp() },
    )

    private fun ContentMatchCandidate.toMvp() = MvpMatchCandidate(
        title = title,
        originalTitle = originalTitle,
        year = year,
        season = season,
        episode = episode,
        confidence = confidence,
    )

    private companion object {
        const val HISTORY_LIMIT = 50
        const val ATTENTION_LIMIT = 20
    }
}
