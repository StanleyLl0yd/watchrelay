package com.sl.watchrelay.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSessionPositionTest {
    @Test
    fun playingPositionUsesMonotonicUpdateTimeAndSpeed() {
        assertEquals(
            7_000,
            MediaSessionPosition.project(
                basePositionMs = 5_000,
                lastUpdateElapsedMs = 10_000,
                nowElapsedMs = 11_000,
                playbackSpeed = 2f,
                durationMs = 20_000,
                playing = true,
            ),
        )
    }

    @Test
    fun pausedPositionDoesNotAdvance() {
        assertEquals(
            5_000,
            MediaSessionPosition.project(
                basePositionMs = 5_000,
                lastUpdateElapsedMs = 10_000,
                nowElapsedMs = 15_000,
                playbackSpeed = 1f,
                durationMs = 20_000,
                playing = false,
            ),
        )
    }

    @Test
    fun projectedPositionIsClampedToDuration() {
        assertEquals(
            20_000,
            MediaSessionPosition.project(
                basePositionMs = 19_000,
                lastUpdateElapsedMs = 10_000,
                nowElapsedMs = 15_000,
                playbackSpeed = 1f,
                durationMs = 20_000,
                playing = true,
            ),
        )
    }
}
