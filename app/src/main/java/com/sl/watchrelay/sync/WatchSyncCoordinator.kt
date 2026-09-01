package com.sl.watchrelay.sync

import android.content.Context
import com.sl.watchrelay.security.KeystoreTokenStore
import com.sl.watchrelay.storage.WatchRelayDatabase

class WatchSyncCoordinator(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val store = RoomSyncQueueStore(WatchRelayDatabase.get(appContext).watchStoreDao())
    private val tokenStore = KeystoreTokenStore(appContext)

    suspend fun recordCompletedWatch(watch: CompletedWatch): Boolean {
        val inserted = store.recordWatch(watch)
        if (inserted) SyncWorkScheduler.schedule(appContext)
        return inserted
    }

    suspend fun undo(eventId: String, createdAtMs: Long = System.currentTimeMillis()): Boolean {
        val enqueued = store.enqueueUndo(eventId, createdAtMs)
        if (enqueued) SyncWorkScheduler.schedule(appContext)
        return enqueued
    }

    suspend fun saveMyShowsToken(token: String) {
        tokenStore.save(token)
        store.resumeProvider(TrackerProvider.MYSHOWS)
        SyncWorkScheduler.schedule(appContext)
    }

    fun clearMyShowsToken() {
        tokenStore.clear()
    }
}
