package com.sl.watchrelay.storage

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.sqlite.driver.AndroidSQLiteDriver

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val eventId: String,
    val itemKey: String,
    val viewedMs: Long,
    val durationMs: Long,
    val watchedAtMs: Long,
    val provider: String,
    val remoteType: String,
    val remoteId: Int,
    val previousRemoteState: String?,
    val syncState: String,
)

@Entity(
    tableName = "pending_sync",
    indices = [Index(value = ["eventId"])],
)
data class PendingSyncEntity(
    @PrimaryKey val operationId: String,
    val eventId: String,
    val provider: String,
    val purpose: String,
    val operationType: String,
    val remoteId: Int,
    val value: String?,
    val previousValue: String?,
    val state: String,
    val attemptCount: Int,
    val createdAtMs: Long,
    val lastAttemptAtMs: Long?,
    val lastError: String?,
)

@Dao
abstract class WatchStoreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertHistory(entity: WatchHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPending(entity: PendingSyncEntity): Long

    @Transaction
    open suspend fun recordWatch(
        history: WatchHistoryEntity,
        pending: PendingSyncEntity,
    ): Boolean {
        if (insertHistory(history) == -1L) return false
        check(insertPending(pending) != -1L) { "Failed to enqueue sync operation for a new watch event" }
        return true
    }

    @Transaction
    open suspend fun enqueueUndo(
        eventId: String,
        pending: PendingSyncEntity,
    ): Boolean {
        if (historyById(eventId) == null) return false
        if (insertPending(pending) == -1L) return false
        updateHistoryState(eventId, "UNDO_PENDING")
        return true
    }

    @Query(
        """
        SELECT * FROM pending_sync
        WHERE state = 'PENDING'
        ORDER BY createdAtMs, operationId
        LIMIT 1
        """,
    )
    abstract suspend fun nextPending(): PendingSyncEntity?

    @Query(
        """
        UPDATE pending_sync
        SET state = :state,
            attemptCount = attemptCount + 1,
            lastAttemptAtMs = :attemptedAtMs,
            lastError = :error
        WHERE operationId = :operationId
        """,
    )
    abstract suspend fun recordAttempt(
        operationId: String,
        state: String,
        attemptedAtMs: Long,
        error: String?,
    )

    @Query("UPDATE watch_history SET syncState = :state WHERE eventId = :eventId")
    abstract suspend fun updateHistoryState(eventId: String, state: String)

    @Query(
        """
        UPDATE pending_sync
        SET state = 'PENDING', lastError = NULL
        WHERE provider = :provider AND state = 'AUTH_REQUIRED'
        """,
    )
    abstract suspend fun resumeAuthRequired(provider: String): Int

    @Query(
        """
        UPDATE watch_history
        SET syncState = 'PENDING'
        WHERE provider = :provider AND syncState = 'AUTH_REQUIRED'
        """,
    )
    abstract suspend fun resumeAuthRequiredHistory(provider: String): Int

    @Transaction
    open suspend fun resumeProvider(provider: String): Int {
        resumeAuthRequiredHistory(provider)
        return resumeAuthRequired(provider)
    }

    @Query("SELECT * FROM watch_history ORDER BY watchedAtMs DESC LIMIT :limit")
    abstract suspend fun recentHistory(limit: Int): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE eventId = :eventId LIMIT 1")
    abstract suspend fun historyById(eventId: String): WatchHistoryEntity?

    @Query("SELECT * FROM pending_sync WHERE operationId = :operationId LIMIT 1")
    abstract suspend fun pendingById(operationId: String): PendingSyncEntity?

    @Query("SELECT COUNT(*) FROM pending_sync WHERE state = 'PENDING'")
    abstract suspend fun pendingCount(): Int
}

@Database(
    entities = [WatchHistoryEntity::class, PendingSyncEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WatchRelayDatabase : RoomDatabase() {
    abstract fun watchStoreDao(): WatchStoreDao

    companion object {
        @Volatile
        private var instance: WatchRelayDatabase? = null

        fun get(context: Context): WatchRelayDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WatchRelayDatabase::class.java,
                "watchrelay.db",
            )
                .setDriver(AndroidSQLiteDriver())
                .build()
                .also { instance = it }
        }
    }
}
