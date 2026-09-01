package com.sl.watchrelay.playback

import com.sl.watchrelay.matching.CompletedPlaybackDecision
import com.sl.watchrelay.matching.CompletedWatchResolution
import com.sl.watchrelay.matching.MetadataNormalizer
import com.sl.watchrelay.matching.PlaybackMetadata
import com.sl.watchrelay.matching.SourceMediaKind
import java.security.MessageDigest

fun interface CompletedDecisionResolver {
    suspend fun resolve(decision: CompletedPlaybackDecision): CompletedWatchResolution
}

data class PlaybackTrackingInput(
    val observation: PlaybackObservation,
    val metadata: PlaybackMetadata,
)

data class PlaybackTrackingUpdate(
    val engineUpdate: PlaybackEngineUpdate,
    val resolution: CompletedWatchResolution? = null,
)

class PlaybackTrackingPipeline(
    watchedThreshold: Double,
    private val resolver: CompletedDecisionResolver,
) {
    private val engine = PlaybackEngine(watchedThreshold = watchedThreshold)
    private var context: SessionContext? = null

    suspend fun accept(input: PlaybackTrackingInput): PlaybackTrackingUpdate {
        val observation = input.observation
        val previousContext = context
        val itemChanged = previousContext != null && previousContext.itemKey != observation.itemKey
        val engineUpdate = engine.accept(observation)
        var resolution: CompletedWatchResolution? = null

        if (itemChanged) {
            engineUpdate.completed?.let { completed ->
                resolution = resolveIfNeeded(previousContext, completed, force = true)
            }
            context = if (observation.status == PlaybackStatus.STOPPED) {
                null
            } else {
                SessionContext.create(observation, input.metadata)
            }
        } else if (previousContext == null && observation.status != PlaybackStatus.STOPPED) {
            context = SessionContext.create(observation, input.metadata)
        } else if (previousContext != null && observation.observedAtMs >= previousContext.lastMetadataAtMs) {
            previousContext.metadata = mergeMetadata(previousContext.metadata, input.metadata)
            previousContext.lastMetadataAtMs = observation.observedAtMs
        }

        val activeContext = context
        if (engineUpdate.active != null && activeContext != null && !activeContext.delivered) {
            if (engineUpdate.active.becameWatched) {
                resolution = resolveIfNeeded(activeContext, engineUpdate.active, force = false) ?: resolution
            }
        }

        if (!itemChanged && engineUpdate.completed != null && previousContext != null) {
            resolution = resolveIfNeeded(previousContext, engineUpdate.completed, force = true) ?: resolution
            context = null
        }

        return PlaybackTrackingUpdate(engineUpdate = engineUpdate, resolution = resolution)
    }

    private suspend fun resolveIfNeeded(
        session: SessionContext,
        snapshot: PlaybackSessionSnapshot,
        force: Boolean,
    ): CompletedWatchResolution? {
        if (!snapshot.watched || session.delivered) return null
        val signature = MetadataNormalizer.normalize(session.metadata).mappingSignature
        if (!force && session.lastResolutionSignature == signature) return null

        val result = resolver.resolve(
            CompletedPlaybackDecision(
                eventId = session.eventId,
                itemKey = session.itemKey,
                viewedMs = snapshot.viewedMs,
                durationMs = snapshot.durationMs,
                watchedAtMs = session.lastMetadataAtMs,
                metadata = session.metadata,
            ),
        )
        session.lastResolutionSignature = signature
        session.delivered = result !is CompletedWatchResolution.Unresolved
        return result
    }

    private class SessionContext(
        val itemKey: String,
        val eventId: String,
        var metadata: PlaybackMetadata,
        var lastMetadataAtMs: Long,
        var lastResolutionSignature: String? = null,
        var delivered: Boolean = false,
    ) {
        companion object {
            fun create(observation: PlaybackObservation, metadata: PlaybackMetadata) = SessionContext(
                itemKey = observation.itemKey,
                eventId = eventId(observation.itemKey, observation.observedAtMs),
                metadata = metadata,
                lastMetadataAtMs = observation.observedAtMs,
            )

            private fun eventId(itemKey: String, startedAtMs: Long): String {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest("$itemKey|$startedAtMs".toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                return "watch-$digest"
            }
        }
    }

    private companion object {
        fun mergeMetadata(previous: PlaybackMetadata, current: PlaybackMetadata) = PlaybackMetadata(
            title = current.title ?: previous.title,
            subtitle = current.subtitle ?: previous.subtitle,
            originalTitle = current.originalTitle ?: previous.originalTitle,
            year = current.year ?: previous.year,
            season = current.season ?: previous.season,
            episode = current.episode ?: previous.episode,
            imdbId = current.imdbId ?: previous.imdbId,
            kinopoiskId = current.kinopoiskId ?: previous.kinopoiskId,
            mediaKind = if (current.mediaKind != SourceMediaKind.UNKNOWN) {
                current.mediaKind
            } else {
                previous.mediaKind
            },
        )
    }
}
