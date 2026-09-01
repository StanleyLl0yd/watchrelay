package com.sl.watchrelay.playback

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

data class MediaSessionSnapshot(
    val packageName: String,
    val title: String?,
    val subtitle: String?,
    val mediaId: String?,
    val durationMs: Long?,
    val positionMs: Long?,
    val playbackState: String,
    val metadataKeys: List<String>,
    val extrasKeys: List<String>,
)

class MediaSessionProbe(private val context: Context) {
    private val component = ComponentName(context, NotificationAccessService::class.java)
    private val manager = context.getSystemService(MediaSessionManager::class.java)

    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun readActiveSessions(): Result<List<MediaSessionSnapshot>> = runCatching {
        check(hasNotificationAccess()) { "Notification access is not granted" }
        manager.getActiveSessions(component).map(::snapshot)
    }

    private fun snapshot(controller: MediaController): MediaSessionSnapshot {
        val metadata = controller.metadata
        val state = controller.playbackState
        return MediaSessionSnapshot(
            packageName = controller.packageName,
            title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata.text(MediaMetadata.METADATA_KEY_TITLE),
            subtitle = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: metadata.text(MediaMetadata.METADATA_KEY_ARTIST),
            mediaId = metadata.text(MediaMetadata.METADATA_KEY_MEDIA_ID),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            positionMs = state?.position?.takeIf { it >= 0 },
            playbackState = stateName(state?.state),
            metadataKeys = metadata?.keySet()?.sorted().orEmpty(),
            extrasKeys = controller.extras?.keySet()?.sorted().orEmpty(),
        )
    }

    private fun MediaMetadata?.text(key: String): String? =
        this?.getText(key)?.toString()?.takeIf(String::isNotBlank)

    private fun stateName(state: Int?): String = when (state) {
        PlaybackState.STATE_NONE -> "NONE"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
        PlaybackState.STATE_REWINDING -> "REWINDING"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_ERROR -> "ERROR"
        PlaybackState.STATE_CONNECTING -> "CONNECTING"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
        null -> "UNKNOWN"
        else -> state.toString()
    }
}
