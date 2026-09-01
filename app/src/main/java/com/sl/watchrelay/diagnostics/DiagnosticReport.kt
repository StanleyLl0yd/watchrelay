package com.sl.watchrelay.diagnostics

data class DiagnosticReportData(
    val appVersion: String,
    val sdkInt: Int,
    val device: String,
    val notificationAccess: Boolean,
    val myShowsConnected: Boolean,
    val pendingSyncCount: Int,
    val authRequiredCount: Int,
    val failedSyncCount: Int,
    val watchedThresholdPercent: Int,
)

object DiagnosticReport {
    fun build(data: DiagnosticReportData): String = buildString {
        appendLine("WatchRelay diagnostic report")
        appendLine("version=${data.appVersion}")
        appendLine("sdk=${data.sdkInt}")
        appendLine("device=${sanitize(data.device)}")
        appendLine("notification_access=${data.notificationAccess}")
        appendLine("myshows_connected=${data.myShowsConnected}")
        appendLine("pending_sync=${data.pendingSyncCount}")
        appendLine("auth_required=${data.authRequiredCount}")
        appendLine("failed_sync=${data.failedSyncCount}")
        appendLine("watched_threshold=${data.watchedThresholdPercent}")
        appendLine("media_metadata=not_exported")
        appendLine("history_titles=not_exported")
        appendLine("credentials=not_exported")
        appendLine("playback_urls=not_exported")
    }

    private fun sanitize(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(MAX_DEVICE_LENGTH)

    private const val MAX_DEVICE_LENGTH = 120
}
