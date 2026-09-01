package com.sl.watchrelay.playback

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.sl.watchrelay.matching.CompletedWatchResolver
import com.sl.watchrelay.matching.PlaybackMetadata
import com.sl.watchrelay.matching.SourceMediaKind
import com.sl.watchrelay.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class NotificationAccessService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter = MediaSessionPlaybackAdapter()
    private val sessions = mutableMapOf<String, ActiveSession>()
    private val bridgeMetadataStore by lazy { BridgeMetadataStore(applicationContext) }
    private var pollingJob: Job? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        startPolling()
    }

    override fun onListenerDisconnected() {
        pollingJob?.cancel()
        pollingJob = null
        sessions.clear()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        sessions.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = serviceScope.launch {
            while (isActive) {
                val playing = pollActiveSessions()
                delay(if (playing) ACTIVE_POLL_MS else IDLE_POLL_MS)
            }
        }
    }

    private suspend fun pollActiveSessions(): Boolean {
        val manager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, NotificationAccessService::class.java)
        val controllers = runCatching { manager.getActiveSessions(component) }.getOrDefault(emptyList())
        val elapsedRealtimeMs = SystemClock.elapsedRealtime()
        val wallClockMs = System.currentTimeMillis()
        val seen = mutableSetOf<String>()
        var anyPlaying = false

        for (controller in controllers) {
            val rawInput = adapter.toInput(controller, elapsedRealtimeMs, wallClockMs) ?: continue
            val sessionKey = sessionKey(controller)
            seen += sessionKey
            val existing = sessions[sessionKey]
            val itemChanged = existing?.lastInput?.observation?.itemKey != rawInput.observation.itemKey
            val bridgeMetadata = if (itemChanged && rawInput.observation.status == PlaybackStatus.PLAYING) {
                bridgeMetadataStore.consume(controller.packageName, wallClockMs)
            } else {
                null
            }
            val input = if (bridgeMetadata == null) rawInput else rawInput.copy(
                metadata = mergeMetadata(rawInput.metadata, bridgeMetadata),
            )
            val active = existing ?: newSession().also { sessions[sessionKey] = it }
            active.lastInput = input
            runCatching { active.pipeline.accept(input) }
            if (input.observation.status == PlaybackStatus.PLAYING) anyPlaying = true
            if (input.observation.status == PlaybackStatus.STOPPED) sessions.remove(sessionKey)
        }

        val disappeared = sessions.keys.filterNot(seen::contains)
        for (sessionKey in disappeared) {
            sessions.remove(sessionKey)?.let { active ->
                runCatching { active.finish(elapsedRealtimeMs, wallClockMs) }
            }
        }
        return anyPlaying
    }

    private fun newSession(): ActiveSession {
        val resolver = CompletedWatchResolver(applicationContext)
        val pipeline = PlaybackTrackingPipeline(
            watchedThreshold = AppSettings(applicationContext).watchedThreshold,
            resolver = CompletedDecisionResolver { decision -> resolver.resolve(decision) },
        )
        return ActiveSession(pipeline)
    }

    private fun sessionKey(controller: MediaController): String =
        "${controller.packageName}:${controller.sessionToken.hashCode()}"

    private fun mergeMetadata(session: PlaybackMetadata, bridge: PlaybackMetadata) = PlaybackMetadata(
        title = bridge.title ?: session.title,
        subtitle = bridge.subtitle ?: session.subtitle,
        originalTitle = bridge.originalTitle ?: session.originalTitle,
        year = bridge.year ?: session.year,
        season = bridge.season ?: session.season,
        episode = bridge.episode ?: session.episode,
        imdbId = bridge.imdbId ?: session.imdbId,
        kinopoiskId = bridge.kinopoiskId ?: session.kinopoiskId,
        mediaKind = if (bridge.mediaKind != SourceMediaKind.UNKNOWN) bridge.mediaKind else session.mediaKind,
    )

    private class ActiveSession(
        val pipeline: PlaybackTrackingPipeline,
        var lastInput: PlaybackTrackingInput? = null,
    ) {
        suspend fun finish(elapsedRealtimeMs: Long, wallClockMs: Long) {
            val last = lastInput ?: return
            val previous = last.observation
            val position = if (previous.status == PlaybackStatus.PLAYING) {
                val elapsed = (elapsedRealtimeMs - previous.observedAtMs).coerceAtLeast(0)
                (previous.positionMs + (elapsed * previous.playbackSpeed).roundToLong())
                    .coerceAtMost(previous.durationMs)
            } else {
                previous.positionMs
            }
            pipeline.accept(
                last.copy(
                    observation = previous.copy(
                        positionMs = position,
                        status = PlaybackStatus.STOPPED,
                        observedAtMs = elapsedRealtimeMs,
                    ),
                    wallClockMs = wallClockMs,
                ),
            )
        }
    }

    private companion object {
        const val ACTIVE_POLL_MS = 2_000L
        const val IDLE_POLL_MS = 10_000L
    }
}
