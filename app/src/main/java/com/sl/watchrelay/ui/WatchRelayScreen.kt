package com.sl.watchrelay.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sl.watchrelay.BuildConfig
import com.sl.watchrelay.diagnostics.DiagnosticReport
import com.sl.watchrelay.diagnostics.DiagnosticReportData
import com.sl.watchrelay.playback.MediaSessionProbe
import com.sl.watchrelay.playback.MediaSessionSnapshot
import com.sl.watchrelay.settings.AppSettings
import com.sl.watchrelay.sync.HistorySyncState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class AppSection {
    HOME,
    SETUP,
    HISTORY,
    SETTINGS,
    DIAGNOSTICS,
}

@Composable
fun WatchRelayScreen(
    repository: MvpRepository,
    resumeVersion: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionProbe = remember { MediaSessionProbe(context.applicationContext) }
    var section by remember { mutableStateOf(AppSection.HOME) }
    var snapshot by remember { mutableStateOf<MvpSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notificationAccess by remember { mutableStateOf(sessionProbe.hasNotificationAccess()) }
    var initialRouteApplied by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            busy = true
            runCatching { repository.snapshot() }
                .onSuccess {
                    snapshot = it
                    notificationAccess = sessionProbe.hasNotificationAccess()
                    error = null
                }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            runCatching { action() }
                .onSuccess { error = null }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
            refresh()
        }
    }

    LaunchedEffect(resumeVersion) {
        busy = true
        runCatching { repository.snapshot() }
            .onSuccess {
                snapshot = it
                notificationAccess = sessionProbe.hasNotificationAccess()
                error = null
                if (!initialRouteApplied) {
                    section = if (it.onboardingCompleted) AppSection.HOME else AppSection.SETUP
                    initialRouteApplied = true
                }
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        busy = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("WatchRelay", style = MaterialTheme.typography.headlineMedium)
        Text("Automatic watch tracking companion")

        SectionNavigation(selected = section, onSelect = { section = it })
        HorizontalDivider()

        if (busy && snapshot == null) Text("Loading…")
        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

        when (section) {
            AppSection.HOME -> HomeSection(
                snapshot = snapshot,
                notificationAccess = notificationAccess,
                onRefresh = ::refresh,
                onSync = { runAction { repository.syncNow() } },
                onSetup = { section = AppSection.SETUP },
                onRetryMatch = { eventId -> runAction { repository.retryMatch(eventId) } },
                onConfirmMatch = { eventId, candidateIndex ->
                    runAction { repository.confirmMatch(eventId, candidateIndex) }
                },
                onDismissMatch = { eventId -> runAction { repository.dismissMatch(eventId) } },
            )

            AppSection.SETUP -> SetupSection(
                authenticated = snapshot?.authenticated == true,
                notificationAccess = notificationAccess,
                busy = busy,
                onOpenNotificationAccess = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onAuthenticate = { login, password, completed ->
                    scope.launch {
                        busy = true
                        runCatching { repository.authenticate(login, password) }
                            .onSuccess {
                                error = null
                                completed(true, null)
                            }
                            .onFailure {
                                val message = it.message ?: it.javaClass.simpleName
                                error = message
                                completed(false, message)
                            }
                        busy = false
                        refresh()
                    }
                },
                onDisconnect = { runAction { repository.disconnect() } },
            )

            AppSection.HISTORY -> HistorySection(
                items = snapshot?.history.orEmpty(),
                onUndo = { eventId ->
                    scope.launch {
                        busy = true
                        runCatching { repository.undo(eventId) }
                            .onSuccess { enqueued ->
                                error = if (enqueued) null else "Undo is not available for this item."
                            }
                            .onFailure { error = it.message ?: it.javaClass.simpleName }
                        busy = false
                        refresh()
                    }
                },
            )

            AppSection.SETTINGS -> SettingsSection(
                initialThreshold = snapshot?.watchedThresholdPercent
                    ?: AppSettings.DEFAULT_THRESHOLD_PERCENT,
                onSave = { value -> runAction { repository.setWatchedThresholdPercent(value) } },
            )

            AppSection.DIAGNOSTICS -> DiagnosticsSection(
                sessionProbe = sessionProbe,
                snapshot = snapshot,
                notificationAccess = notificationAccess,
            )
        }
    }
}

@Composable
private fun SectionNavigation(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavButton("Home", selected == AppSection.HOME) { onSelect(AppSection.HOME) }
            NavButton("Setup", selected == AppSection.SETUP) { onSelect(AppSection.SETUP) }
            NavButton("History", selected == AppSection.HISTORY) { onSelect(AppSection.HISTORY) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavButton("Settings", selected == AppSection.SETTINGS) { onSelect(AppSection.SETTINGS) }
            NavButton("Diagnostics", selected == AppSection.DIAGNOSTICS) { onSelect(AppSection.DIAGNOSTICS) }
        }
    }
}

@Composable
private fun NavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun HomeSection(
    snapshot: MvpSnapshot?,
    notificationAccess: Boolean,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onSetup: () -> Unit,
    onRetryMatch: (String) -> Unit,
    onConfirmMatch: (String, Int) -> Unit,
    onDismissMatch: (String) -> Unit,
) {
    val state = snapshot
    Text("Status", style = MaterialTheme.typography.titleLarge)
    Text("MyShows: ${if (state?.authenticated == true) "connected" else "not connected"}")
    Text("Playback observation access: ${if (notificationAccess) "granted" else "required"}")
    Text("Pending sync: ${state?.pendingCount ?: 0}")
    Text("Needs authentication: ${state?.authRequiredCount ?: 0}")
    Text("Failed: ${state?.failedCount ?: 0}")
    Text("Matches to resolve: ${state?.matchAttention?.size ?: 0}")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRefresh) { Text("Refresh") }
        Button(onClick = onSync) { Text("Sync now") }
        if (state?.authenticated != true || !notificationAccess) {
            OutlinedButton(onClick = onSetup) { Text("Finish setup") }
        }
    }

    if (state?.matchAttention?.isNotEmpty() == true || state?.attention?.isNotEmpty() == true) {
        HorizontalDivider()
        Text("Needs attention", style = MaterialTheme.typography.titleLarge)
    }

    state?.matchAttention.orEmpty().forEach { item ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.itemKey, style = MaterialTheme.typography.titleMedium)
            Text(formatTime(item.watchedAtMs))
            Text(item.reason)
            if (state?.authenticated != true) {
                Text("Reconnect MyShows before resolving this watch.")
            }
            if (item.candidates.isEmpty()) {
                Button(
                    enabled = state?.authenticated == true,
                    onClick = { onRetryMatch(item.eventId) },
                ) {
                    Text("Retry matching")
                }
            } else {
                item.candidates.forEachIndexed { index, candidate ->
                    OutlinedButton(
                        enabled = state?.authenticated == true,
                        onClick = { onConfirmMatch(item.eventId, index) },
                    ) {
                        Text("Use ${candidateLabel(candidate)}")
                    }
                }
            }
            OutlinedButton(onClick = { onDismissMatch(item.eventId) }) {
                Text("Ignore this watch")
            }
        }
        HorizontalDivider()
    }

    state?.attention.orEmpty().forEach { item ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${item.state} · ${shortId(item.eventId)}", style = MaterialTheme.typography.titleMedium)
            Text("Attempts: ${item.attempts}")
            item.message?.takeIf(String::isNotBlank)?.let { Text(it) }
        }
    }

    HorizontalDivider()
    Text("Recent history", style = MaterialTheme.typography.titleLarge)
    val recent = state?.history.orEmpty().take(5)
    if (recent.isEmpty()) Text("No tracked watches yet.")
    else recent.forEach { HistorySummary(it, showUndo = false, onUndo = {}) }
}

@Composable
private fun SetupSection(
    authenticated: Boolean,
    notificationAccess: Boolean,
    busy: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onAuthenticate: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onDisconnect: () -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localStatus by remember { mutableStateOf<String?>(null) }

    Text("Setup", style = MaterialTheme.typography.titleLarge)
    Text("1. Allow WatchRelay to inspect active Android media sessions.")
    Text("Notification access: ${if (notificationAccess) "granted" else "required"}")
    Button(onClick = onOpenNotificationAccess) { Text("Open notification access") }

    HorizontalDivider()
    Text("2. Connect a regular MyShows account.")
    Text("WatchRelay stores only the resulting session token, encrypted with Android Keystore. The password is never persisted.")

    if (authenticated) {
        Text("MyShows is connected.")
        OutlinedButton(enabled = !busy, onClick = onDisconnect) { Text("Disconnect MyShows") }
    } else {
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("MyShows login") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("MyShows password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !busy && login.isNotBlank() && password.isNotEmpty(),
            onClick = {
                onAuthenticate(login, password) { success, message ->
                    if (success) {
                        password = ""
                        localStatus = "Connected."
                    } else {
                        localStatus = message
                    }
                }
            },
        ) {
            Text("Connect MyShows")
        }
    }
    localStatus?.let { Text(it) }

    HorizontalDivider()
    Text("Automatic LMD/player support is still experimental until each path is validated on real devices. A missed mark is preferred to a wrong mark.")
}

@Composable
private fun HistorySection(
    items: List<MvpHistoryItem>,
    onUndo: (String) -> Unit,
) {
    Text("History", style = MaterialTheme.typography.titleLarge)
    if (items.isEmpty()) {
        Text("No tracked watches yet.")
        return
    }
    items.forEach { item ->
        HistorySummary(item, showUndo = item.canUndo, onUndo = { onUndo(item.eventId) })
        HorizontalDivider()
    }
}

@Composable
private fun HistorySummary(
    item: MvpHistoryItem,
    showUndo: Boolean,
    onUndo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(item.itemKey, style = MaterialTheme.typography.titleMedium)
        Text("${item.remoteType.name.lowercase()} #${item.remoteId} · viewed ${item.viewedPercent}%")
        Text("${formatTime(item.watchedAtMs)} · ${formatSyncState(item.syncState)}")
        if (showUndo) OutlinedButton(onClick = onUndo) { Text("Undo watched mark") }
    }
}

@Composable
private fun SettingsSection(
    initialThreshold: Int,
    onSave: (Int) -> Unit,
) {
    var threshold by remember(initialThreshold) { mutableStateOf(initialThreshold.toFloat()) }
    val rounded = threshold.roundToInt().coerceIn(
        AppSettings.MIN_THRESHOLD_PERCENT,
        AppSettings.MAX_THRESHOLD_PERCENT,
    )

    Text("Settings", style = MaterialTheme.typography.titleLarge)
    Text("Watched threshold: $rounded%")
    Slider(
        value = threshold,
        onValueChange = { threshold = it },
        valueRange = AppSettings.MIN_THRESHOLD_PERCENT.toFloat()..AppSettings.MAX_THRESHOLD_PERCENT.toFloat(),
        steps = AppSettings.MAX_THRESHOLD_PERCENT - AppSettings.MIN_THRESHOLD_PERCENT - 1,
    )
    Button(onClick = { onSave(rounded) }) { Text("Save threshold") }
    Text("The threshold applies to newly created playback sessions. Default: ${AppSettings.DEFAULT_THRESHOLD_PERCENT}%.")

    HorizontalDivider()
    Text("Privacy", style = MaterialTheme.typography.titleLarge)
    Text("Playback analysis and history are local-first. WatchRelay has no account, backend, advertising, or telemetry requirement. Tracker credentials are not written to Room or logs.")
}

@Composable
private fun DiagnosticsSection(
    sessionProbe: MediaSessionProbe,
    snapshot: MvpSnapshot?,
    notificationAccess: Boolean,
) {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf<List<MediaSessionSnapshot>>(emptyList()) }
    var status by remember { mutableStateOf("Not scanned yet") }

    Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
    Text("MediaSession probe remains available for Phase 0 compatibility validation.")
    Button(onClick = {
        sessionProbe.readActiveSessions().fold(
            onSuccess = {
                sessions = it
                status = "Found ${it.size} active media session(s)"
            },
            onFailure = {
                sessions = emptyList()
                status = it.message ?: "MediaSession scan failed"
            },
        )
    }) {
        Text("Scan active sessions")
    }
    Text(status)
    sessions.forEach { session ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.title ?: "Untitled session", style = MaterialTheme.typography.titleMedium)
            Text("Package: ${session.packageName}")
            Text("State: ${session.playbackState}")
            Text("Position: ${formatDuration(session.positionMs)} / ${formatDuration(session.durationMs)}")
            Text("Metadata keys: ${session.metadataKeys.joinToString().ifBlank { "—" }}")
        }
    }

    HorizontalDivider()
    Text("Safe diagnostic export", style = MaterialTheme.typography.titleMedium)
    Text("The exported report contains app/device and aggregate state only. Titles, media metadata, history content, credentials and playback URLs are excluded.")
    Button(
        enabled = snapshot != null,
        onClick = {
            val state = snapshot ?: return@Button
            val report = DiagnosticReport.build(
                DiagnosticReportData(
                    appVersion = BuildConfig.VERSION_NAME,
                    sdkInt = Build.VERSION.SDK_INT,
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    notificationAccess = notificationAccess,
                    myShowsConnected = state.authenticated,
                    pendingSyncCount = state.pendingCount,
                    authRequiredCount = state.authRequiredCount,
                    failedSyncCount = state.failedCount,
                    watchedThresholdPercent = state.watchedThresholdPercent,
                ),
            )
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "WatchRelay diagnostics")
                .putExtra(Intent.EXTRA_TEXT, report)
            context.startActivity(Intent.createChooser(intent, "Share WatchRelay diagnostics"))
        },
    ) {
        Text("Share redacted report")
    }

    HorizontalDivider()
    Text("External-player intent probe")
    Text("The existing sanitized video intent handler remains registered for LMD/external-player investigation. It never persists the playback URI.")
}

private fun candidateLabel(candidate: MvpMatchCandidate): String = buildString {
    append(candidate.title ?: candidate.originalTitle ?: "candidate")
    candidate.year?.let { append(" ($it)") }
    if (candidate.season != null && candidate.episode != null) {
        append(" S%02dE%02d".format(candidate.season, candidate.episode))
    }
    append(" · ${candidate.confidence}%")
}

private fun formatSyncState(state: HistorySyncState): String = when (state) {
    HistorySyncState.PENDING -> "pending"
    HistorySyncState.SYNCED -> "synced"
    HistorySyncState.AUTH_REQUIRED -> "reconnect MyShows"
    HistorySyncState.FAILED -> "failed"
    HistorySyncState.UNDO_PENDING -> "undo pending"
    HistorySyncState.UNDONE -> "undone"
}

private fun formatTime(value: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
).format(Date(value))

private fun formatDuration(value: Long?): String {
    if (value == null) return "—"
    val totalSeconds = value / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun shortId(value: String): String = value.take(8)
