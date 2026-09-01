package com.sl.watchrelay.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sl.watchrelay.myshows.MyShowsFreeClient
import com.sl.watchrelay.myshows.MyShowsSyncExecutor
import com.sl.watchrelay.security.KeystoreTokenStore
import com.sl.watchrelay.storage.WatchRelayDatabase
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = RoomSyncQueueStore(WatchRelayDatabase.get(applicationContext).watchStoreDao())
        val executor = MyShowsSyncExecutor(
            MyShowsFreeClient(),
            KeystoreTokenStore(applicationContext),
        )
        return when (SyncQueueProcessor(store, executor).drain()) {
            QueueDrainResult.IDLE,
            QueueDrainResult.DRAINED,
            QueueDrainResult.AUTH_REQUIRED,
            -> Result.success()
            QueueDrainResult.RETRY -> Result.retry()
        }
    }
}

object SyncWorkScheduler {
    private const val UNIQUE_WORK_NAME = "watchrelay-tracker-sync"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
