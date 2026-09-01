package com.sl.watchrelay.playback

import com.sl.watchrelay.matching.CompletedWatchResolution
import com.sl.watchrelay.matching.ContentMatchResult
import com.sl.watchrelay.matching.MatchEvidence
import com.sl.watchrelay.matching.PendingMatch
import com.sl.watchrelay.matching.PendingMatchState
import com.sl.watchrelay.matching.PlaybackMetadata
import com.sl.watchrelay.matching.ResolvedContent
import com.sl.watchrelay.matching.SourceMediaKind
import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.TrackerProvider
import com.sl.watchrelay.sync.TrackerTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackingPipelineTest {
    @Test
    fun thresholdCrossingCreatesOneCompletedDecision() = runBlocking {
        val decisions = mutableListOf<String>()
        val pipeline = PlaybackTrackingPipeline(0.8) { decision ->
            decisions += decision.eventId
            queuedMovie()
        }
        val metadata = PlaybackMetadata(title = "Dune", year = 2021, mediaKind = SourceMediaKind.MOVIE)

        pipeline.accept(input(0, 0, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(4_000, 4_000, PlaybackStatus.PLAYING, metadata))
        val threshold = pipeline.accept(input(8_000, 8_000, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(9_000, 9_000, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(9_000, 10_000, PlaybackStatus.STOPPED, metadata))

        assertTrue(threshold.resolution is CompletedWatchResolution.Queued)
        assertEquals(1, decisions.size)
        assertTrue(decisions.single().startsWith("watch-"))
    }

    @Test
    fun retryRequiredDecisionIsDeliveredOnceAndLeftForDurableAttention() = runBlocking {
        var calls = 0
        val pipeline = PlaybackTrackingPipeline(0.8) { decision ->
            calls++
            CompletedWatchResolution.RetryRequired(
                PendingMatch(
                    eventId = decision.eventId,
                    itemKey = decision.itemKey,
                    viewedMs = decision.viewedMs,
                    durationMs = decision.durationMs,
                    watchedAtMs = decision.watchedAtMs,
                    metadata = decision.metadata,
                    state = PendingMatchState.RETRY_REQUIRED,
                    candidates = emptyList(),
                    reason = "Retry when online",
                ),
            )
        }
        val metadata = PlaybackMetadata(title = "Dune", year = 2021, mediaKind = SourceMediaKind.MOVIE)

        pipeline.accept(input(0, 0, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(8_000, 8_000, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(9_000, 9_000, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(9_000, 10_000, PlaybackStatus.STOPPED, metadata))

        assertEquals(1, calls)
    }

    @Test
    fun unresolvedThresholdGetsOneFinalAttemptAtSessionEnd() = runBlocking {
        var calls = 0
        val pipeline = PlaybackTrackingPipeline(0.8) {
            calls++
            CompletedWatchResolution.Unresolved("Not enough metadata")
        }
        val metadata = PlaybackMetadata(title = "Unknown")

        pipeline.accept(input(0, 0, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(8_000, 8_000, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(8_500, 8_500, PlaybackStatus.PLAYING, metadata))
        pipeline.accept(input(8_500, 9_000, PlaybackStatus.STOPPED, metadata))

        assertEquals(2, calls)
    }

    @Test
    fun laterMetadataIsUsedWhenThresholdIsReached() = runBlocking {
        var resolvedTitle: String? = null
        val pipeline = PlaybackTrackingPipeline(0.8) { decision ->
            resolvedTitle = decision.metadata.title
            queuedMovie()
        }

        pipeline.accept(input(0, 0, PlaybackStatus.PLAYING, PlaybackMetadata(title = null)))
        pipeline.accept(
            input(
                4_000,
                4_000,
                PlaybackStatus.PLAYING,
                PlaybackMetadata(title = "Dune", year = 2021, mediaKind = SourceMediaKind.MOVIE),
            ),
        )
        pipeline.accept(
            input(
                8_000,
                8_000,
                PlaybackStatus.PLAYING,
                PlaybackMetadata(title = "Dune", year = 2021, mediaKind = SourceMediaKind.MOVIE),
            ),
        )

        assertEquals("Dune", resolvedTitle)
    }

    private fun input(
        positionMs: Long,
        observedAtMs: Long,
        status: PlaybackStatus,
        metadata: PlaybackMetadata,
    ) = PlaybackTrackingInput(
        observation = PlaybackObservation(
            itemKey = "player:item",
            positionMs = positionMs,
            durationMs = 10_000,
            status = status,
            observedAtMs = observedAtMs,
        ),
        metadata = metadata,
    )

    private fun queuedMovie() = CompletedWatchResolution.Queued(
        ContentMatchResult.Confirmed(
            content = ResolvedContent(
                target = TrackerTarget(
                    provider = TrackerProvider.MYSHOWS,
                    mediaType = RemoteMediaType.MOVIE,
                    remoteId = 10,
                    previousRemoteState = "later",
                ),
                title = "Dune",
                originalTitle = "Dune",
                year = 2021,
            ),
            confidence = 100,
            evidence = MatchEvidence.TITLE_AND_YEAR,
        ),
    )
}
