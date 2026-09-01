package com.sl.watchrelay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sl.watchrelay.myshows.MyShowsFreeClient
import com.sl.watchrelay.playback.MediaSessionProbe
import com.sl.watchrelay.playback.MediaSessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TechnicalProofScreen()
                }
            }
        }
    }
}

@Composable
private fun TechnicalProofScreen() {
    val context = LocalContext.current
    val sessionProbe = remember { MediaSessionProbe(context.applicationContext) }
    var accessGranted by remember { mutableStateOf(sessionProbe.hasNotificationAccess()) }
    var sessions by remember { mutableStateOf<List<MediaSessionSnapshot>>(emptyList()) }
    var sessionStatus by remember { mutableStateOf("Not scanned yet") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("WatchRelay", style = MaterialTheme.typography.headlineMedium)
        Text("Phase 0 technical proof — no automatic watched-state tracking yet.")

        Text("MediaSession probe", style = MaterialTheme.typography.titleLarge)
        Text(if (accessGranted) "Notification access: granted" else "Notification access: required")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("Open access settings")
            }
            Button(onClick = {
                accessGranted = sessionProbe.hasNotificationAccess()
                sessionProbe.readActiveSessions().fold(
                    onSuccess = {
                        sessions = it
                        sessionStatus = "Found ${it.size} active media session(s)"
                    },
                    onFailure = {
                        sessions = emptyList()
                        sessionStatus = it.message ?: "MediaSession scan failed"
                    },
                )
            }) {
                Text("Refresh")
            }
        }
        Text(sessionStatus)
        for (session in sessions) {
            MediaSessionCard(session)
        }

        HorizontalDivider()
        Text("External-player intent probe", style = MaterialTheme.typography.titleLarge)
        Text(
            "Select WatchRelay as a video handler in a supported source app to inspect the incoming action, MIME type and non-sensitive extras. The diagnostic activity never stores the media URI and does not forward playback yet.",
        )

        HorizontalDivider()
        MyShowsFreeProbe()
    }
}

@Composable
private fun MediaSessionCard(session: MediaSessionSnapshot) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(session.title ?: "Untitled session", style = MaterialTheme.typography.titleMedium)
        Text("Package: ${session.packageName}")
        session.subtitle?.let { Text("Subtitle: $it") }
        session.mediaId?.let { Text("Media ID: $it") }
        Text("State: ${session.playbackState}")
        Text("Position: ${formatDuration(session.positionMs)} / ${formatDuration(session.durationMs)}")
        Text("Metadata keys: ${session.metadataKeys.joinToString().ifBlank { "—" }}")
        Text("Session extras keys: ${session.extrasKeys.joinToString().ifBlank { "—" }}")
    }
}

@Composable
private fun MyShowsFreeProbe() {
    val client = remember { MyShowsFreeClient() }
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var movieId by remember { mutableStateOf("") }
    var episodeId by remember { mutableStateOf("") }
    var previousMovieStatus by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Not authenticated") }
    var busy by remember { mutableStateOf(false) }

    fun runProbe(block: () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            status = runCatching { withContext(Dispatchers.IO) { block() } }
                .fold(
                    onSuccess = { it },
                    onFailure = { "Error: ${it.message ?: it.javaClass.simpleName}" },
                )
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MyShows Free probe", style = MaterialTheme.typography.titleLarge)
        Text(
            "This diagnostic uses the ordinary MyShows session and JSON-RPC endpoints, never the Pro scrobble API. Mutating buttons below change your MyShows account. Credentials are kept only in memory; the password is cleared after successful authentication.",
        )
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
                if (busy) return@Button
                val submittedLogin = login
                val submittedPassword = password
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        client.authenticate(submittedLogin, submittedPassword)
                    }
                    result.fold(
                        onSuccess = {
                            token = it
                            password = ""
                            status = "Authenticated. Session token is held in memory only."
                        },
                        onFailure = {
                            status = "Error: ${it.message ?: it.javaClass.simpleName}"
                        },
                    )
                    busy = false
                }
            },
        ) {
            Text("Authenticate")
        }

        OutlinedTextField(
            value = movieId,
            onValueChange = { movieId = it.filter(Char::isDigit) },
            label = { Text("MyShows movie ID") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !busy && token != null && movieId.isNotBlank(),
                onClick = {
                    if (busy) return@Button
                    val id = movieId.toIntOrNull() ?: return@Button
                    val sessionToken = token ?: return@Button
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val before = client.readMovieState(sessionToken, id).getOrThrow()
                                val restore = before.watchStatus ?: "remove"
                                client.setMovieStatus(sessionToken, id, "finished").getOrThrow()
                                Triple(before.title, restore, id)
                            }
                        }
                        result.fold(
                            onSuccess = { (title, restore, testedId) ->
                                previousMovieStatus = restore
                                status = "Movie ${title ?: testedId} marked finished; previous status: $restore."
                            },
                            onFailure = {
                                status = "Error: ${it.message ?: it.javaClass.simpleName}"
                            },
                        )
                        busy = false
                    }
                },
            ) {
                Text("Mark movie watched")
            }
            Button(
                enabled = !busy && token != null && movieId.isNotBlank() && previousMovieStatus != null,
                onClick = {
                    val id = movieId.toIntOrNull() ?: return@Button
                    val sessionToken = token ?: return@Button
                    val restore = previousMovieStatus ?: return@Button
                    runProbe {
                        client.setMovieStatus(sessionToken, id, restore).getOrThrow()
                        "Movie status restored to $restore."
                    }
                },
            ) {
                Text("Undo movie")
            }
        }

        OutlinedTextField(
            value = episodeId,
            onValueChange = { episodeId = it.filter(Char::isDigit) },
            label = { Text("MyShows episode ID") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !busy && token != null && episodeId.isNotBlank(),
                onClick = {
                    val id = episodeId.toIntOrNull() ?: return@Button
                    val sessionToken = token ?: return@Button
                    runProbe {
                        client.checkEpisode(sessionToken, id).getOrThrow()
                        "Episode $id marked watched."
                    }
                },
            ) {
                Text("Mark episode watched")
            }
            Button(
                enabled = !busy && token != null && episodeId.isNotBlank(),
                onClick = {
                    val id = episodeId.toIntOrNull() ?: return@Button
                    val sessionToken = token ?: return@Button
                    runProbe {
                        client.uncheckEpisode(sessionToken, id).getOrThrow()
                        "Episode $id unmarked."
                    }
                },
            ) {
                Text("Undo episode")
            }
        }
        Text(if (busy) "Working…" else status)
    }
}

private fun formatDuration(value: Long?): String {
    if (value == null) return "—"
    val totalSeconds = value / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
