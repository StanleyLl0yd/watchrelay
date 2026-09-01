package com.sl.watchrelay.playback

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

enum class PlaybackStatus {
    PLAYING,
    PAUSED,
    STOPPED,
}

enum class PlaybackEndReason {
    STOPPED,
    ITEM_CHANGED,
}

data class PlaybackObservation(
    val itemKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val status: PlaybackStatus,
    val observedAtMs: Long,
    val playbackSpeed: Float = 1f,
) {
    init {
        require(itemKey.isNotBlank())
        require(positionMs >= 0)
        require(durationMs >= 0)
        require(observedAtMs >= 0)
        require(playbackSpeed >= 0f)
    }
}

data class PlaybackSessionSnapshot(
    val itemKey: String,
    val viewedMs: Long,
    val durationMs: Long,
    val watchedFraction: Double,
    val watched: Boolean,
    val becameWatched: Boolean,
    val ended: Boolean,
    val endReason: PlaybackEndReason? = null,
)

data class PlaybackEngineUpdate(
    val active: PlaybackSessionSnapshot? = null,
    val completed: PlaybackSessionSnapshot? = null,
)

class PlaybackEngine(
    watchedThreshold: Double = DEFAULT_WATCHED_THRESHOLD,
    private val seekToleranceMs: Long = DEFAULT_SEEK_TOLERANCE_MS,
) {
    private val watchedThreshold = watchedThreshold.also { require(it > 0.0 && it <= 1.0) }
    private var activeSession: Session? = null

    init {
        require(seekToleranceMs >= 0)
    }

    fun accept(observation: PlaybackObservation): PlaybackEngineUpdate {
        val current = activeSession
        if (current == null) {
            if (observation.status == PlaybackStatus.STOPPED) return PlaybackEngineUpdate()
            val session = Session(observation)
            activeSession = session
            return PlaybackEngineUpdate(active = session.snapshot())
        }

        if (current.itemKey != observation.itemKey) {
            current.clearTransientState()
            val completed = current.finish(PlaybackEndReason.ITEM_CHANGED)
            if (observation.status == PlaybackStatus.STOPPED) {
                activeSession = null
                return PlaybackEngineUpdate(completed = completed)
            }

            val replacement = Session(observation)
            activeSession = replacement
            return PlaybackEngineUpdate(active = replacement.snapshot(), completed = completed)
        }

        current.accept(observation)
        if (observation.status == PlaybackStatus.STOPPED) {
            activeSession = null
            return PlaybackEngineUpdate(completed = current.finish(PlaybackEndReason.STOPPED))
        }

        return PlaybackEngineUpdate(active = current.snapshot())
    }

    private inner class Session(initial: PlaybackObservation) {
        val itemKey = initial.itemKey
        private val intervals = ViewedIntervals()
        private var previous = initial
        private var durationMs = initial.durationMs
        private var wasWatched = false
        private var becameWatched = false

        fun accept(observation: PlaybackObservation) {
            require(observation.itemKey == itemKey)
            if (observation.observedAtMs < previous.observedAtMs) return

            durationMs = max(durationMs, observation.durationMs)
            becameWatched = false

            val elapsedMs = observation.observedAtMs - previous.observedAtMs
            if (previous.status == PlaybackStatus.PLAYING && elapsedMs > 0) {
                val start = previous.positionMs.coerceAtMost(durationLimit())
                val end = observation.positionMs.coerceAtMost(durationLimit())
                val positionDelta = end - start
                val expectedDelta = (elapsedMs * previous.playbackSpeed).roundToLong()

                if (positionDelta > 0 && abs(positionDelta - expectedDelta) <= seekToleranceMs) {
                    intervals.add(start, end)
                }
            }

            previous = observation
            val watched = isWatched()
            becameWatched = watched && !wasWatched
            wasWatched = watched
        }

        fun clearTransientState() {
            becameWatched = false
        }

        fun snapshot(): PlaybackSessionSnapshot = snapshot(ended = false, endReason = null)

        fun finish(reason: PlaybackEndReason): PlaybackSessionSnapshot =
            snapshot(ended = true, endReason = reason)

        private fun durationLimit(): Long = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE

        private fun isWatched(): Boolean =
            durationMs > 0 && intervals.totalMs.toDouble() / durationMs >= watchedThreshold

        private fun snapshot(
            ended: Boolean,
            endReason: PlaybackEndReason?,
        ): PlaybackSessionSnapshot {
            val viewedMs = intervals.totalMs
            val fraction = if (durationMs > 0) {
                (viewedMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            return PlaybackSessionSnapshot(
                itemKey = itemKey,
                viewedMs = viewedMs,
                durationMs = durationMs,
                watchedFraction = fraction,
                watched = fraction >= watchedThreshold,
                becameWatched = becameWatched,
                ended = ended,
                endReason = endReason,
            )
        }
    }

    private class ViewedIntervals {
        private val intervals = mutableListOf<LongRange>()

        val totalMs: Long
            get() = intervals.sumOf { it.last - it.first }

        fun add(startMs: Long, endMs: Long) {
            if (endMs <= startMs) return

            var start = startMs
            var end = endMs
            val merged = ArrayList<LongRange>(intervals.size + 1)
            var inserted = false

            for (interval in intervals) {
                val intervalStart = interval.first
                val intervalEnd = interval.last
                when {
                    intervalEnd < start -> merged += interval
                    end < intervalStart -> {
                        if (!inserted) {
                            merged += start..end
                            inserted = true
                        }
                        merged += interval
                    }
                    else -> {
                        start = minOf(start, intervalStart)
                        end = maxOf(end, intervalEnd)
                    }
                }
            }

            if (!inserted) merged += start..end
            intervals.clear()
            intervals += merged
        }
    }

    companion object {
        const val DEFAULT_WATCHED_THRESHOLD = 0.8
        const val DEFAULT_SEEK_TOLERANCE_MS = 2_000L
    }
}
