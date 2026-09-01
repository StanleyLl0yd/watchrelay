package com.sl.watchrelay.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import com.sl.watchrelay.matching.PlaybackMetadata
import java.security.MessageDigest
import kotlin.math.roundToLong

class MediaSessionPlaybackAdapter {
    fun toInput(
        controller: MediaController,
        elapsedRealtimeMs: Long,
        wallClockMs: Long,
    ): PlaybackTrackingInput? {
        val metadata = controller.metadata ?: return null
        val state = controller.playbackState ?: return null
        val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 } ?: return null
        val title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_TITLE)
        val subtitle = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
        val mediaId = metadata.text(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val stableIdentity = mediaId ?: listOfNotNull(title, subtitle).joinToString("|").takeIf(String::isNotBlank)
            ?: return null
        val status = state.toPlaybackStatus()
        val speed = state.playbackSpeed.coerceAtLeast(0f)
        val positionMs = MediaSessionPosition.project(
            basePositionMs = state.position.coerceAtLeast(0),
            lastUpdateElapsedMs = state.lastPositionUpdateTime,
            nowElapsedMs = elapsedRealtimeMs,
            playbackSpeed = speed,
            durationMs = durationMs,
            playing = status == PlaybackStatus.PLAYING,
        )
        val year = metadata.getLong(MediaMetadata.METADATA_KEY_YEAR)
            .toInt()
            .takeIf { it in 1900..2100 }
        val imdbId = mediaId?.trim()?.takeIf { IMDB_ID.matches(it) }

        return PlaybackTrackingInput(
            observation = PlaybackObservation(
                itemKey = safeItemKey(controller.packageName, stableIdentity),
                positionMs = positionMs,
                durationMs = durationMs,
                status = status,
                observedAtMs = elapsedRealtimeMs,
                playbackSpeed = speed,
            ),
            metadata = PlaybackMetadata(
                title = title,
                subtitle = subtitle,
                year = year,
                imdbId = imdbId,
            ),
            wallClockMs = wallClockMs,
        )
    }

    private fun MediaMetadata.text(key: String): String? =
        getText(key)?.toString()?.trim()?.takeIf(String::isNotBlank)

    private fun PlaybackState.toPlaybackStatus(): PlaybackStatus = when (state) {
        PlaybackState.STATE_PLAYING -> PlaybackStatus.PLAYING
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_NONE,
        PlaybackState.STATE_ERROR,
        -> PlaybackStatus.STOPPED
        else -> PlaybackStatus.PAUSED
    }

    private fun safeItemKey(packageName: String, stableIdentity: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(stableIdentity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "media-session:$packageName:${digest.take(24)}"
    }

    private companion object {
        val IMDB_ID = Regex("(?i)^tt\\d{5,10}$")
    }
}

object MediaSessionPosition {
    fun project(
        basePositionMs: Long,
        lastUpdateElapsedMs: Long,
        nowElapsedMs: Long,
        playbackSpeed: Float,
        durationMs: Long,
        playing: Boolean,
    ): Long {
        val safeBase = basePositionMs.coerceAtLeast(0)
        if (!playing || playbackSpeed <= 0f || nowElapsedMs <= lastUpdateElapsedMs) {
            return safeBase.coerceAtMost(durationMs)
        }
        val elapsed = nowElapsedMs - lastUpdateElapsedMs
        val projected = safeBase + (elapsed * playbackSpeed).roundToLong()
        return projected.coerceIn(0, durationMs)
    }
}
