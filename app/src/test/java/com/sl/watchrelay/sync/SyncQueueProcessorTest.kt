package com.sl.watchrelay.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueProcessorTest {
    @Test
    fun watchOperationIdIsDeterministicAndUndoIsDistinct() = runBlocking {
        val watch = movieWatch(previousState = "later")
        val first = SyncMutation.forWatch(watch)
        val second = SyncMutation.forWatch(watch.copy(watchedAtMs = watch.watchedAtMs + 10_000))
        val store = FakeStore()
        assertTrue(store.recordWatch(watch))
        val history = store.historyById(watch.eventId)!!
        val undo = SyncMutation.forUndo(history, createdAtMs = 2_000)

        assertEquals(first.operationId, second.operationId)
        assertNotEquals(first.operationId, undo.operationId)
        assertEquals(SyncOperationType.SET_MOVIE_STATUS, first.type)
        assertEquals("finished", first.value)
        assertEquals("later", undo.value)
    }

    @Test
    fun duplicateWatchDoesNotCreateSecondMutation() = runBlocking {
        val store = FakeStore()
        val watch = episodeWatch()

        assertTrue(store.recordWatch(watch))
        assertFalse(store.recordWatch(watch))
        assertEquals(1, store.pending.size)
    }

    @Test
    fun retryableFailureSurvivesProcessorRestartAndThenSucceeds() = runBlocking {
        val store = FakeStore()
        store.recordWatch(episodeWatch())

        val first = SyncQueueProcessor(
            store,
            SyncExecutor { SyncExecutionResult.RetryableFailure("offline") },
            nowMs = { 2_000 },
        )
        assertEquals(QueueDrainResult.RETRY, first.drain())
        assertEquals(1, store.pending.single().attemptCount)
        assertEquals(SyncQueueState.PENDING, store.states.values.single())
        assertEquals(HistorySyncState.PENDING, store.history.values.single().syncState)

        val restarted = SyncQueueProcessor(
            store,
            SyncExecutor { SyncExecutionResult.Success },
            nowMs = { 3_000 },
        )
        assertEquals(QueueDrainResult.DRAINED, restarted.drain())
        assertEquals(SyncQueueState.SUCCEEDED, store.states.values.single())
        assertEquals(HistorySyncState.SYNCED, store.history.values.single().syncState)
    }

    @Test
    fun authenticationFailureStopsUntilProviderIsResumed() = runBlocking {
        val store = FakeStore()
        store.recordWatch(episodeWatch())
        val processor = SyncQueueProcessor(
            store,
            SyncExecutor { SyncExecutionResult.AuthenticationRequired("expired") },
            nowMs = { 2_000 },
        )

        assertEquals(QueueDrainResult.AUTH_REQUIRED, processor.drain())
        assertEquals(SyncQueueState.AUTH_REQUIRED, store.states.values.single())
        assertEquals(HistorySyncState.AUTH_REQUIRED, store.history.values.single().syncState)
        assertNull(store.nextPending())

        assertEquals(1, store.resumeProvider(TrackerProvider.MYSHOWS))
        assertEquals(SyncQueueState.PENDING, store.states.values.single())
        assertEquals(HistorySyncState.PENDING, store.history.values.single().syncState)
    }

    @Test
    fun episodeUndoUsesUncheckAndBecomesUndone() = runBlocking {
        val store = FakeStore()
        val watch = episodeWatch()
        store.recordWatch(watch)
        SyncQueueProcessor(store, SyncExecutor { SyncExecutionResult.Success }).drain()

        assertTrue(store.enqueueUndo(watch.eventId, 2_000))
        val undo = store.nextPending()!!
        assertEquals(SyncPurpose.UNDO, undo.purpose)
        assertEquals(SyncOperationType.UNCHECK_EPISODE, undo.type)

        assertEquals(
            QueueDrainResult.DRAINED,
            SyncQueueProcessor(store, SyncExecutor { SyncExecutionResult.Success }).drain(),
        )
        assertEquals(HistorySyncState.UNDONE, store.historyById(watch.eventId)!!.syncState)
        assertFalse(store.enqueueUndo(watch.eventId, 3_000))
    }

    @Test
    fun episodeUndoRestoresAlreadyWatchedRemoteState() = runBlocking {
        val store = FakeStore()
        val watch = episodeWatch(previousState = SyncMutation.EPISODE_WATCHED_STATE)
        store.recordWatch(watch)
        SyncQueueProcessor(store, SyncExecutor { SyncExecutionResult.Success }).drain()

        assertTrue(store.enqueueUndo(watch.eventId, 2_000))
        val undo = store.nextPending()!!
        assertEquals(SyncPurpose.UNDO, undo.purpose)
        assertEquals(SyncOperationType.CHECK_EPISODE, undo.type)
    }

    @Test
    fun episodeUndoIsRejectedWhenPreviousRemoteStateIsUnknown() = runBlocking {
        val store = FakeStore()
        val watch = episodeWatch(previousState = null)
        store.recordWatch(watch)
        SyncQueueProcessor(store, SyncExecutor { SyncExecutionResult.Success }).drain()

        assertFalse(store.enqueueUndo(watch.eventId, 2_000))
        assertEquals(HistorySyncState.SYNCED, store.historyById(watch.eventId)!!.syncState)
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun authenticationFailureDuringUndoResumesAsUndoPending() = runBlocking {
        val store = FakeStore()
        val watch = episodeWatch()
        store.recordWatch(watch)
        SyncQueueProcessor(store, SyncExecutor { SyncExecutionResult.Success }).drain()
        assertTrue(store.enqueueUndo(watch.eventId, 2_000))

        val blocked = SyncQueueProcessor(
            store,
            SyncExecutor { SyncExecutionResult.AuthenticationRequired("expired") },
            nowMs = { 3_000 },
        )
        assertEquals(QueueDrainResult.AUTH_REQUIRED, blocked.drain())
        assertEquals(HistorySyncState.AUTH_REQUIRED, store.historyById(watch.eventId)!!.syncState)

        assertEquals(1, store.resumeProvider(TrackerProvider.MYSHOWS))
        assertEquals(HistorySyncState.UNDO_PENDING, store.historyById(watch.eventId)!!.syncState)

        val resumed = SyncQueueProcessor(
            store,
            SyncExecutor { SyncExecutionResult.Success },
            nowMs = { 4_000 },
        )
        assertEquals(QueueDrainResult.DRAINED, resumed.drain())
        assertEquals(HistorySyncState.UNDONE, store.historyById(watch.eventId)!!.syncState)
    }

    @Test
    fun movieUndoWithoutPreviousStateFallsBackToRemove() = runBlocking {
        val store = FakeStore()
        val watch = movieWatch(previousState = null)
        store.recordWatch(watch)
        SyncQueueProcessor(store, SyncExecutor { SyncExecutionResult.Success }).drain()

        assertTrue(store.enqueueUndo(watch.eventId, 2_000))
        val undo = store.nextPending()!!
        assertEquals(SyncOperationType.SET_MOVIE_STATUS, undo.type)
        assertEquals("remove", undo.value)
    }

    @Test
    fun permanentFailureDoesNotSpinAndQueueCanContinue() = runBlocking {
        val store = FakeStore()
        store.recordWatch(episodeWatch(eventId = "event-a", remoteId = 10))
        store.recordWatch(episodeWatch(eventId = "event-b", remoteId = 11, watchedAtMs = 2_000))
        var calls = 0
        val processor = SyncQueueProcessor(
            store,
            SyncExecutor {
                calls++
                if (calls == 1) SyncExecutionResult.PermanentFailure("bad request")
                else SyncExecutionResult.Success
            },
        )

        assertEquals(QueueDrainResult.DRAINED, processor.drain())
        assertEquals(2, calls)
        assertEquals(HistorySyncState.FAILED, store.history.getValue("event-a").syncState)
        assertEquals(HistorySyncState.SYNCED, store.history.getValue("event-b").syncState)
        assertEquals(0, store.pendingCount())
    }

    private fun episodeWatch(
        eventId: String = "event-episode",
        remoteId: Int = 42,
        watchedAtMs: Long = 1_000,
        previousState: String? = SyncMutation.EPISODE_UNWATCHED_STATE,
    ) = CompletedWatch(
        eventId = eventId,
        itemKey = "episode-key",
        viewedMs = 80_000,
        durationMs = 100_000,
        watchedAtMs = watchedAtMs,
        target = TrackerTarget(
            provider = TrackerProvider.MYSHOWS,
            mediaType = RemoteMediaType.EPISODE,
            remoteId = remoteId,
            previousRemoteState = previousState,
        ),
    )

    private fun movieWatch(previousState: String?) = CompletedWatch(
        eventId = "event-movie",
        itemKey = "movie-key",
        viewedMs = 90_000,
        durationMs = 100_000,
        watchedAtMs = 1_000,
        target = TrackerTarget(
            provider = TrackerProvider.MYSHOWS,
            mediaType = RemoteMediaType.MOVIE,
            remoteId = 7,
            previousRemoteState = previousState,
        ),
    )

    private class FakeStore : SyncQueueStore {
        val history = linkedMapOf<String, StoredWatch>()
        val pending = mutableListOf<PendingMutation>()
        val states = linkedMapOf<String, SyncQueueState>()

        override suspend fun recordWatch(watch: CompletedWatch): Boolean {
            if (history.containsKey(watch.eventId)) return false
            history[watch.eventId] = StoredWatch(
                eventId = watch.eventId,
                itemKey = watch.itemKey,
                viewedMs = watch.viewedMs,
                durationMs = watch.durationMs,
                watchedAtMs = watch.watchedAtMs,
                provider = watch.target.provider,
                remoteType = watch.target.mediaType,
                remoteId = watch.target.remoteId,
                previousRemoteState = watch.target.previousRemoteState,
                syncState = HistorySyncState.PENDING,
            )
            add(SyncMutation.forWatch(watch))
            return true
        }

        override suspend fun enqueueUndo(eventId: String, createdAtMs: Long): Boolean {
            val current = history[eventId] ?: return false
            if (current.syncState == HistorySyncState.UNDONE || current.syncState == HistorySyncState.UNDO_PENDING) {
                return false
            }
            if (
                current.remoteType == RemoteMediaType.EPISODE &&
                current.previousRemoteState !in setOf(
                    SyncMutation.EPISODE_WATCHED_STATE,
                    SyncMutation.EPISODE_UNWATCHED_STATE,
                )
            ) {
                return false
            }
            val mutation = SyncMutation.forUndo(current, createdAtMs)
            if (states.containsKey(mutation.operationId)) return false
            add(mutation)
            history[eventId] = current.copy(syncState = HistorySyncState.UNDO_PENDING)
            return true
        }

        override suspend fun nextPending(): PendingMutation? = pending
            .filter { states[it.operationId] == SyncQueueState.PENDING }
            .minWithOrNull(compareBy<PendingMutation> { it.createdAtMs }.thenBy { it.operationId })

        override suspend fun recordAttempt(
            mutation: PendingMutation,
            state: SyncQueueState,
            attemptedAtMs: Long,
            error: String?,
        ) {
            states[mutation.operationId] = state
            val index = pending.indexOfFirst { it.operationId == mutation.operationId }
            if (index >= 0) pending[index] = pending[index].copy(attemptCount = mutation.attemptCount + 1)
        }

        override suspend fun updateHistoryState(eventId: String, state: HistorySyncState) {
            history[eventId]?.let { history[eventId] = it.copy(syncState = state) }
        }

        override suspend fun resumeProvider(provider: TrackerProvider): Int {
            val undoEventIds = pending
                .filter {
                    it.provider == provider &&
                        it.purpose == SyncPurpose.UNDO &&
                        states[it.operationId] == SyncQueueState.AUTH_REQUIRED
                }
                .mapTo(mutableSetOf()) { it.eventId }

            var resumed = 0
            for (mutation in pending) {
                if (mutation.provider == provider && states[mutation.operationId] == SyncQueueState.AUTH_REQUIRED) {
                    states[mutation.operationId] = SyncQueueState.PENDING
                    resumed++
                }
            }
            history.replaceAll { eventId, value ->
                if (value.provider == provider && value.syncState == HistorySyncState.AUTH_REQUIRED) {
                    value.copy(
                        syncState = if (eventId in undoEventIds) {
                            HistorySyncState.UNDO_PENDING
                        } else {
                            HistorySyncState.PENDING
                        },
                    )
                } else value
            }
            return resumed
        }

        override suspend fun historyById(eventId: String): StoredWatch? = history[eventId]

        override suspend fun pendingCount(): Int = states.values.count { it == SyncQueueState.PENDING }

        private fun add(mutation: SyncMutation) {
            pending += PendingMutation(
                operationId = mutation.operationId,
                eventId = mutation.eventId,
                provider = mutation.provider,
                purpose = mutation.purpose,
                type = mutation.type,
                remoteId = mutation.remoteId,
                value = mutation.value,
                previousValue = mutation.previousValue,
                attemptCount = 0,
                createdAtMs = mutation.createdAtMs,
            )
            states[mutation.operationId] = SyncQueueState.PENDING
        }
    }
}
