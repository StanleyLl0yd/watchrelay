package com.sl.watchrelay.matching

import android.content.Context
import com.sl.watchrelay.sync.CompletedWatch
import com.sl.watchrelay.sync.WatchSyncCoordinator

sealed interface CompletedWatchResolution {
    data class Synced(val result: ContentMatchResult.Confirmed) : CompletedWatchResolution
    data class NeedsConfirmation(val pending: PendingMatch) : CompletedWatchResolution
    data class Unresolved(val reason: String) : CompletedWatchResolution
}

data class CompletedPlaybackDecision(
    val eventId: String,
    val itemKey: String,
    val viewedMs: Long,
    val durationMs: Long,
    val watchedAtMs: Long,
    val metadata: PlaybackMetadata,
)

class CompletedWatchResolver(
    context: Context,
    private val contentResolver: ContentResolutionCoordinator = ContentResolutionCoordinator(context),
    private val pendingStore: PendingMatchStore = SharedPreferencesPendingMatchStore(context),
    private val syncCoordinator: WatchSyncCoordinator = WatchSyncCoordinator(context),
) {
    suspend fun resolve(decision: CompletedPlaybackDecision): CompletedWatchResolution {
        return when (val result = contentResolver.resolve(decision.metadata)) {
            is ContentMatchResult.Confirmed -> {
                enqueue(decision, result)
                pendingStore.remove(decision.eventId)
                CompletedWatchResolution.Synced(result)
            }

            is ContentMatchResult.Ambiguous -> {
                val pending = PendingMatch(
                    eventId = decision.eventId,
                    itemKey = decision.itemKey,
                    viewedMs = decision.viewedMs,
                    durationMs = decision.durationMs,
                    watchedAtMs = decision.watchedAtMs,
                    metadata = decision.metadata,
                    candidates = result.candidates,
                    reason = result.reason,
                )
                pendingStore.put(pending)
                CompletedWatchResolution.NeedsConfirmation(pending)
            }

            is ContentMatchResult.Unresolved -> CompletedWatchResolution.Unresolved(result.reason)
        }
    }

    suspend fun pending(): List<PendingMatch> = pendingStore.list()

    suspend fun confirm(eventId: String, candidateIndex: Int): ContentMatchResult.Confirmed {
        val pending = pendingStore.get(eventId) ?: error("Pending content match was not found")
        val candidate = pending.candidates.getOrNull(candidateIndex)
            ?: error("Content match candidate is no longer available")
        val confirmed = contentResolver.confirm(pending.metadata, candidate)
        enqueue(
            CompletedPlaybackDecision(
                eventId = pending.eventId,
                itemKey = pending.itemKey,
                viewedMs = pending.viewedMs,
                durationMs = pending.durationMs,
                watchedAtMs = pending.watchedAtMs,
                metadata = pending.metadata,
            ),
            confirmed,
        )
        pendingStore.remove(eventId)
        return confirmed
    }

    suspend fun dismiss(eventId: String) {
        pendingStore.remove(eventId)
    }

    private suspend fun enqueue(
        decision: CompletedPlaybackDecision,
        confirmed: ContentMatchResult.Confirmed,
    ) {
        syncCoordinator.recordCompletedWatch(
            CompletedWatch(
                eventId = decision.eventId,
                itemKey = decision.itemKey,
                viewedMs = decision.viewedMs,
                durationMs = decision.durationMs,
                watchedAtMs = decision.watchedAtMs,
                target = confirmed.content.target,
            ),
        )
    }
}
