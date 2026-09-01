package com.sl.watchrelay.sync

import com.sl.watchrelay.storage.PendingSyncEntity
import com.sl.watchrelay.storage.WatchHistoryEntity
import com.sl.watchrelay.storage.WatchStoreDao
import java.security.MessageDigest

enum class TrackerProvider {
    MYSHOWS,
}

enum class RemoteMediaType {
    MOVIE,
    EPISODE,
}

enum class SyncPurpose {
    WATCH,
    UNDO,
}

enum class SyncOperationType {
    CHECK_EPISODE,
    UNCHECK_EPISODE,
    SET_MOVIE_STATUS,
}

enum class SyncQueueState {
    PENDING,
    SUCCEEDED,
    AUTH_REQUIRED,
    FAILED,
}

enum class HistorySyncState {
    PENDING,
    SYNCED,
    AUTH_REQUIRED,
    FAILED,
    UNDO_PENDING,
    UNDONE,
}

data class TrackerTarget(
    val provider: TrackerProvider,
    val mediaType: RemoteMediaType,
    val remoteId: Int,
    val previousRemoteState: String? = null,
) {
    init {
        require(remoteId > 0)
    }
}

data class CompletedWatch(
    val eventId: String,
    val itemKey: String,
    val viewedMs: Long,
    val durationMs: Long,
    val watchedAtMs: Long,
    val target: TrackerTarget,
) {
    init {
        require(eventId.isNotBlank())
        require(itemKey.isNotBlank())
        require(viewedMs >= 0)
        require(durationMs > 0)
        require(viewedMs <= durationMs)
        require(watchedAtMs >= 0)
    }
}

data class SyncMutation(
    val operationId: String,
    val eventId: String,
    val provider: TrackerProvider,
    val purpose: SyncPurpose,
    val type: SyncOperationType,
    val remoteId: Int,
    val value: String? = null,
    val previousValue: String? = null,
    val createdAtMs: Long,
) {
    companion object {
        fun forWatch(watch: CompletedWatch): SyncMutation {
            val type = when (watch.target.mediaType) {
                RemoteMediaType.EPISODE -> SyncOperationType.CHECK_EPISODE
                RemoteMediaType.MOVIE -> SyncOperationType.SET_MOVIE_STATUS
            }
            val value = if (watch.target.mediaType == RemoteMediaType.MOVIE) MOVIE_WATCHED_STATUS else null
            return create(
                eventId = watch.eventId,
                provider = watch.target.provider,
                purpose = SyncPurpose.WATCH,
                type = type,
                remoteId = watch.target.remoteId,
                value = value,
                previousValue = watch.target.previousRemoteState,
                createdAtMs = watch.watchedAtMs,
            )
        }

        fun forUndo(history: StoredWatch, createdAtMs: Long): SyncMutation {
            val type: SyncOperationType
            val value: String?
            when (history.remoteType) {
                RemoteMediaType.EPISODE -> {
                    type = SyncOperationType.UNCHECK_EPISODE
                    value = null
                }
                RemoteMediaType.MOVIE -> {
                    type = SyncOperationType.SET_MOVIE_STATUS
                    value = history.previousRemoteState ?: MOVIE_REMOVE_STATUS
                }
            }
            return create(
                eventId = history.eventId,
                provider = history.provider,
                purpose = SyncPurpose.UNDO,
                type = type,
                remoteId = history.remoteId,
                value = value,
                previousValue = MOVIE_WATCHED_STATUS.takeIf { history.remoteType == RemoteMediaType.MOVIE },
                createdAtMs = createdAtMs,
            )
        }

        private fun create(
            eventId: String,
            provider: TrackerProvider,
            purpose: SyncPurpose,
            type: SyncOperationType,
            remoteId: Int,
            value: String?,
            previousValue: String?,
            createdAtMs: Long,
        ): SyncMutation {
            val canonical = listOf(
                eventId,
                provider.name,
                purpose.name,
                type.name,
                remoteId.toString(),
                value.orEmpty(),
            ).joinToString("|")
            return SyncMutation(
                operationId = sha256(canonical),
                eventId = eventId,
                provider = provider,
                purpose = purpose,
                type = type,
                remoteId = remoteId,
                value = value,
                previousValue = previousValue,
                createdAtMs = createdAtMs,
            )
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        const val MOVIE_WATCHED_STATUS = "finished"
        const val MOVIE_REMOVE_STATUS = "remove"
    }
}

data class StoredWatch(
    val eventId: String,
    val itemKey: String,
    val viewedMs: Long,
    val durationMs: Long,
    val watchedAtMs: Long,
    val provider: TrackerProvider,
    val remoteType: RemoteMediaType,
    val remoteId: Int,
    val previousRemoteState: String?,
    val syncState: HistorySyncState,
)

data class PendingMutation(
    val operationId: String,
    val eventId: String,
    val provider: TrackerProvider,
    val purpose: SyncPurpose,
    val type: SyncOperationType,
    val remoteId: Int,
    val value: String?,
    val previousValue: String?,
    val attemptCount: Int,
    val createdAtMs: Long,
)

interface SyncQueueStore {
    suspend fun recordWatch(watch: CompletedWatch): Boolean
    suspend fun enqueueUndo(eventId: String, createdAtMs: Long): Boolean
    suspend fun nextPending(): PendingMutation?
    suspend fun recordAttempt(
        mutation: PendingMutation,
        state: SyncQueueState,
        attemptedAtMs: Long,
        error: String?,
    )
    suspend fun updateHistoryState(eventId: String, state: HistorySyncState)

    suspend fun recordOutcome(
        mutation: PendingMutation,
        queueState: SyncQueueState,
        historyState: HistorySyncState?,
        attemptedAtMs: Long,
        error: String?,
    ) {
        recordAttempt(mutation, queueState, attemptedAtMs, error)
        if (historyState != null) updateHistoryState(mutation.eventId, historyState)
    }

    suspend fun resumeProvider(provider: TrackerProvider): Int
    suspend fun historyById(eventId: String): StoredWatch?
    suspend fun pendingCount(): Int
}

class RoomSyncQueueStore(
    private val dao: WatchStoreDao,
) : SyncQueueStore {
    override suspend fun recordWatch(watch: CompletedWatch): Boolean {
        val mutation = SyncMutation.forWatch(watch)
        return dao.recordWatch(
            history = WatchHistoryEntity(
                eventId = watch.eventId,
                itemKey = watch.itemKey,
                viewedMs = watch.viewedMs,
                durationMs = watch.durationMs,
                watchedAtMs = watch.watchedAtMs,
                provider = watch.target.provider.name,
                remoteType = watch.target.mediaType.name,
                remoteId = watch.target.remoteId,
                previousRemoteState = watch.target.previousRemoteState,
                syncState = HistorySyncState.PENDING.name,
            ),
            pending = mutation.toEntity(),
        )
    }

    override suspend fun enqueueUndo(eventId: String, createdAtMs: Long): Boolean {
        val history = historyById(eventId) ?: return false
        if (history.syncState == HistorySyncState.UNDONE || history.syncState == HistorySyncState.UNDO_PENDING) {
            return false
        }
        return dao.enqueueUndo(eventId, SyncMutation.forUndo(history, createdAtMs).toEntity())
    }

    override suspend fun nextPending(): PendingMutation? = dao.nextPending()?.toDomain()

    override suspend fun recordAttempt(
        mutation: PendingMutation,
        state: SyncQueueState,
        attemptedAtMs: Long,
        error: String?,
    ) {
        dao.recordAttempt(mutation.operationId, state.name, attemptedAtMs, error)
    }

    override suspend fun updateHistoryState(eventId: String, state: HistorySyncState) {
        dao.updateHistoryState(eventId, state.name)
    }

    override suspend fun recordOutcome(
        mutation: PendingMutation,
        queueState: SyncQueueState,
        historyState: HistorySyncState?,
        attemptedAtMs: Long,
        error: String?,
    ) {
        dao.recordOutcome(
            operationId = mutation.operationId,
            eventId = mutation.eventId,
            queueState = queueState.name,
            historyState = historyState?.name,
            attemptedAtMs = attemptedAtMs,
            error = error,
        )
    }

    override suspend fun resumeProvider(provider: TrackerProvider): Int = dao.resumeProvider(provider.name)

    override suspend fun historyById(eventId: String): StoredWatch? = dao.historyById(eventId)?.toDomain()

    override suspend fun pendingCount(): Int = dao.pendingCount()

    private fun SyncMutation.toEntity() = PendingSyncEntity(
        operationId = operationId,
        eventId = eventId,
        provider = provider.name,
        purpose = purpose.name,
        operationType = type.name,
        remoteId = remoteId,
        value = value,
        previousValue = previousValue,
        state = SyncQueueState.PENDING.name,
        attemptCount = 0,
        createdAtMs = createdAtMs,
        lastAttemptAtMs = null,
        lastError = null,
    )

    private fun PendingSyncEntity.toDomain() = PendingMutation(
        operationId = operationId,
        eventId = eventId,
        provider = TrackerProvider.valueOf(provider),
        purpose = SyncPurpose.valueOf(purpose),
        type = SyncOperationType.valueOf(operationType),
        remoteId = remoteId,
        value = value,
        previousValue = previousValue,
        attemptCount = attemptCount,
        createdAtMs = createdAtMs,
    )

    private fun WatchHistoryEntity.toDomain() = StoredWatch(
        eventId = eventId,
        itemKey = itemKey,
        viewedMs = viewedMs,
        durationMs = durationMs,
        watchedAtMs = watchedAtMs,
        provider = TrackerProvider.valueOf(provider),
        remoteType = RemoteMediaType.valueOf(remoteType),
        remoteId = remoteId,
        previousRemoteState = previousRemoteState,
        syncState = HistorySyncState.valueOf(syncState),
    )
}

sealed interface SyncExecutionResult {
    data object Success : SyncExecutionResult
    data class RetryableFailure(val message: String) : SyncExecutionResult
    data class AuthenticationRequired(val message: String) : SyncExecutionResult
    data class PermanentFailure(val message: String) : SyncExecutionResult
}

fun interface SyncExecutor {
    suspend fun execute(mutation: PendingMutation): SyncExecutionResult
}

enum class QueueDrainResult {
    IDLE,
    DRAINED,
    RETRY,
    AUTH_REQUIRED,
}

class SyncQueueProcessor(
    private val store: SyncQueueStore,
    private val executor: SyncExecutor,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun drain(): QueueDrainResult {
        var processedAny = false
        while (true) {
            val mutation = store.nextPending()
                ?: return if (processedAny) QueueDrainResult.DRAINED else QueueDrainResult.IDLE
            val attemptedAt = nowMs()
            when (val result = executor.execute(mutation)) {
                SyncExecutionResult.Success -> {
                    store.recordOutcome(
                        mutation = mutation,
                        queueState = SyncQueueState.SUCCEEDED,
                        historyState = if (mutation.purpose == SyncPurpose.UNDO) {
                            HistorySyncState.UNDONE
                        } else {
                            HistorySyncState.SYNCED
                        },
                        attemptedAtMs = attemptedAt,
                        error = null,
                    )
                    processedAny = true
                }
                is SyncExecutionResult.RetryableFailure -> {
                    store.recordOutcome(
                        mutation = mutation,
                        queueState = SyncQueueState.PENDING,
                        historyState = null,
                        attemptedAtMs = attemptedAt,
                        error = result.message,
                    )
                    return QueueDrainResult.RETRY
                }
                is SyncExecutionResult.AuthenticationRequired -> {
                    store.recordOutcome(
                        mutation = mutation,
                        queueState = SyncQueueState.AUTH_REQUIRED,
                        historyState = HistorySyncState.AUTH_REQUIRED,
                        attemptedAtMs = attemptedAt,
                        error = result.message,
                    )
                    return QueueDrainResult.AUTH_REQUIRED
                }
                is SyncExecutionResult.PermanentFailure -> {
                    store.recordOutcome(
                        mutation = mutation,
                        queueState = SyncQueueState.FAILED,
                        historyState = HistorySyncState.FAILED,
                        attemptedAtMs = attemptedAt,
                        error = result.message,
                    )
                    processedAny = true
                }
            }
        }
    }
}
