package com.sl.watchrelay.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineTest {
    @Test
    fun normalPlaybackCrossesThresholdOnce() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0))
        val halfway = engine.accept(observation(position = 40_000, time = 40_000)).active!!
        val watched = engine.accept(observation(position = 80_000, time = 80_000)).active!!
        val duplicate = engine.accept(observation(position = 90_000, time = 90_000)).active!!

        assertEquals(40_000, halfway.viewedMs)
        assertFalse(halfway.watched)
        assertEquals(80_000, watched.viewedMs)
        assertTrue(watched.watched)
        assertTrue(watched.becameWatched)
        assertTrue(duplicate.watched)
        assertFalse(duplicate.becameWatched)
    }

    @Test
    fun pauseAndResumeDoNotCountPausedTime() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0))
        engine.accept(observation(position = 20_000, time = 20_000, status = PlaybackStatus.PAUSED))
        engine.accept(observation(position = 20_000, time = 50_000, status = PlaybackStatus.PLAYING))
        val snapshot = engine.accept(
            observation(position = 40_000, time = 70_000, status = PlaybackStatus.PAUSED),
        ).active!!

        assertEquals(40_000, snapshot.viewedMs)
        assertFalse(snapshot.watched)
    }

    @Test
    fun forwardSeekDoesNotCountSkippedContent() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0))
        engine.accept(observation(position = 10_000, time = 10_000))
        engine.accept(observation(position = 80_000, time = 11_000))
        val snapshot = engine.accept(observation(position = 90_000, time = 21_000)).active!!

        assertEquals(20_000, snapshot.viewedMs)
        assertFalse(snapshot.watched)
    }

    @Test
    fun backwardSeekDoesNotDoubleCountRewatchedRange() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0))
        engine.accept(observation(position = 50_000, time = 50_000))
        engine.accept(observation(position = 20_000, time = 51_000))
        val snapshot = engine.accept(observation(position = 50_000, time = 81_000)).active!!

        assertEquals(50_000, snapshot.viewedMs)
        assertFalse(snapshot.watched)
    }

    @Test
    fun duplicateCallbacksDoNotAddProgressTwice() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0))
        engine.accept(observation(position = 10_000, time = 10_000))
        engine.accept(observation(position = 10_000, time = 10_000))
        val snapshot = engine.accept(observation(position = 20_000, time = 20_000)).active!!

        assertEquals(20_000, snapshot.viewedMs)
    }

    @Test
    fun abruptStopClosesSessionWithoutFalseWatchedDecision() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0))
        engine.accept(observation(position = 70_000, time = 70_000))
        val update = engine.accept(
            observation(position = 75_000, time = 75_000, status = PlaybackStatus.STOPPED),
        )

        assertNull(update.active)
        assertNotNull(update.completed)
        assertEquals(75_000, update.completed!!.viewedMs)
        assertFalse(update.completed!!.watched)
        assertEquals(PlaybackEndReason.STOPPED, update.completed!!.endReason)
    }

    @Test
    fun replayStartsFreshSessionAfterStop() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0))
        val first = engine.accept(
            observation(position = 80_000, time = 80_000, status = PlaybackStatus.STOPPED),
        ).completed!!
        val replay = engine.accept(observation(position = 0, time = 90_000)).active!!
        val replayProgress = engine.accept(observation(position = 10_000, time = 100_000)).active!!

        assertTrue(first.watched)
        assertTrue(first.ended)
        assertEquals(0, replay.viewedMs)
        assertFalse(replay.watched)
        assertEquals(10_000, replayProgress.viewedMs)
    }

    @Test
    fun autoplayTransitionEndsPreviousItemAndStartsNext() {
        val engine = PlaybackEngine()

        engine.accept(observation(item = "episode-a", position = 0, time = 0))
        val threshold = engine.accept(
            observation(item = "episode-a", position = 80_000, time = 80_000),
        ).active!!
        val transition = engine.accept(
            observation(item = "episode-b", position = 0, time = 81_000),
        )

        assertTrue(threshold.becameWatched)
        assertNotNull(transition.completed)
        assertEquals("episode-a", transition.completed!!.itemKey)
        assertEquals(PlaybackEndReason.ITEM_CHANGED, transition.completed!!.endReason)
        assertTrue(transition.completed!!.watched)
        assertFalse(transition.completed!!.becameWatched)
        assertNotNull(transition.active)
        assertEquals("episode-b", transition.active!!.itemKey)
        assertEquals(0, transition.active!!.viewedMs)
    }

    @Test
    fun playbackSpeedIsIncludedInSeekDetection() {
        val engine = PlaybackEngine()

        engine.accept(observation(position = 0, time = 0, speed = 2f))
        val snapshot = engine.accept(observation(position = 20_000, time = 10_000, speed = 2f)).active!!

        assertEquals(20_000, snapshot.viewedMs)
    }

    private fun observation(
        item: String = "movie",
        position: Long,
        time: Long,
        status: PlaybackStatus = PlaybackStatus.PLAYING,
        duration: Long = 100_000,
        speed: Float = 1f,
    ) = PlaybackObservation(
        itemKey = item,
        positionMs = position,
        durationMs = duration,
        status = status,
        observedAtMs = time,
        playbackSpeed = speed,
    )
}
