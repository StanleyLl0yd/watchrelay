package com.sl.watchrelay.matching

import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.TrackerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PendingMatchCodecTest {
    @Test
    fun ambiguousCompletedWatchRoundTripsWithoutPlaybackUrls() {
        val source = PendingMatch(
            eventId = "event-1",
            itemKey = "player:item-42",
            viewedMs = 4_800,
            durationMs = 6_000,
            watchedAtMs = 100,
            metadata = PlaybackMetadata(
                title = "The Office S02E03",
                originalTitle = "The Office",
                year = 2005,
                season = 2,
                episode = 3,
                imdbId = "tt0386676",
                mediaKind = SourceMediaKind.EPISODE,
            ),
            state = PendingMatchState.AMBIGUOUS,
            candidates = listOf(
                ContentMatchCandidate(
                    mapping = SavedContentMapping(
                        provider = TrackerProvider.MYSHOWS,
                        mediaType = RemoteMediaType.EPISODE,
                        remoteId = 203,
                        showId = 20,
                    ),
                    title = "The Office",
                    originalTitle = "The Office",
                    year = 2005,
                    season = 2,
                    episode = 3,
                    confidence = 90,
                ),
            ),
            reason = "User confirmation is required",
        )

        val encoded = PendingMatchCodec.encode(source)
        val decoded = PendingMatchCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(source, decoded)
        assertEquals(false, encoded.contains("http://"))
        assertEquals(false, encoded.contains("https://"))
    }

    @Test
    fun retryRequiredWatchCanPersistWithoutCandidates() {
        val source = PendingMatch(
            eventId = "event-offline",
            itemKey = "player:item-offline",
            viewedMs = 8_000,
            durationMs = 10_000,
            watchedAtMs = 200,
            metadata = PlaybackMetadata(
                title = "Dune",
                year = 2021,
                mediaKind = SourceMediaKind.MOVIE,
            ),
            state = PendingMatchState.RETRY_REQUIRED,
            candidates = emptyList(),
            reason = "Retry when online",
        )

        assertEquals(source, PendingMatchCodec.decode(PendingMatchCodec.encode(source)))
    }

    @Test
    fun invalidStoredValueIsIgnored() {
        assertEquals(null, PendingMatchCodec.decode("not-json"))
    }
}
