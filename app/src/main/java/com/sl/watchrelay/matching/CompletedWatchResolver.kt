package com.sl.watchrelay.matching

import android.content.Context
import com.sl.watchrelay.sync.CompletedWatch
import com.sl.watchrelay.sync.WatchSyncCoordinator
import java.io.IOException

sealed interface CompletedWatchResolution {
    data class Queued(val result: ContentMatchResult.Confirmed) : CompletedWatchResolution
    data class NeedsConfirmation(val pending: PendingMatch) : CompletedWatchResolution
    data class RetryRequired(val pending: PendingMatch) : CompletedWatchResolution
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
        val result = try {
            contentResolver.resolve(decision.metadata)
        } catch (_: IOException) {
            val pending = decision.toPending(
                state = PendingMatchState.RETRY_REQUIRED,
                reason = "Content matching could not be completed. Retry when the tracker is reachable.",
            )
            pendingStore.put(pending)
            return CompletedWatchResolution.RetryRequired(pending)
        }

        return when (result) {
            is ContentMatchResult.Confirmed -> {
                enqueue(decision, result)
                pendingStore.remove(decision.eventId)
                CompletedWatchResolution.Queued(result)
            }

            is ContentMatchResult.Ambiguous -> {
                val pending = decision.toPending(
                    state = PendingMatchState.AMBIGUOUS,
                    candidates = result.candidates,
                    reason = result.reason,
                )
                pendingStore.put(pending)
                CompletedWatchResolution.NeedsConfirmation(pending)
            }

            is ContentMatchResult.Unresolved -> {
                pendingStore.remove(decision.eventId)
                CompletedWatchResolution.Unresolved(result.reason)
            }
        }
    }

    suspend fun pending(): List<PendingMatch> = pendingStore.list()

    suspend fun retry(eventId: String): CompletedWatchResolution {
        val pending = pendingStore.get(eventId) ?: error("Pending content match was not found")
        return resolve(pending.toDecision())
    }

    suspend fun confirm(eventId: String, candidateIndex: Int): ContentMatchResult.Confirmed {
        val pending = pendingStore.get(eventId) ?: error("Pending content match was not found")
        check(pending.state == PendingMatchState.AMBIGUOUS) { "This content match requires a retry, not confirmation" }
        val candidate = pending.candidates.getOrNull(candidateIndex)
            ?: error("Content match candidate is no longer available")
        val confirmed = contentResolver.confirm(pending.metadata, candidate)
        enqueue(pending.toDecision(), confirmed)
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

    private fun CompletedPlaybackDecision.toPending(
        state: PendingMatchState,
        candidates: List<ContentMatchCandidate> = emptyList(),
        reason: String,
    ) = PendingMatch(
        eventId = eventId,
        itemKey = itemKey,
        viewedMs = viewedMs,
        durationMs = durationMs,
        watchedAtMs = watchedAtMs,
        metadata = metadata,
        state = state,
        candidates = candidates,
        reason = reason,
    )

    private fun PendingMatch.toDecision() = CompletedPlaybackDecision(
        eventId = eventId,
        itemKey = itemKey,
        viewedMs = viewedMs,
        durationMs = durationMs,
        watchedAtMs = watchedAtMs,
        metadata = metadata,
    )
}
