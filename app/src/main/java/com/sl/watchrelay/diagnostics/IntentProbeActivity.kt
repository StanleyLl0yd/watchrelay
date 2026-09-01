package com.sl.watchrelay.diagnostics

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sl.watchrelay.matching.PlaybackMetadata
import com.sl.watchrelay.playback.BridgeMetadataStore
import com.sl.watchrelay.playback.ExternalPlayerPreferences
import com.sl.watchrelay.playback.IntentPlaybackMetadataExtractor

data class ExternalPlayerOption(
    val packageName: String,
    val label: String,
)

class IntentProbeActivity : ComponentActivity() {
    private var snapshot by mutableStateOf<IntentProbeSnapshot?>(null)
    private var players by mutableStateOf<List<ExternalPlayerOption>>(emptyList())
    private var error by mutableStateOf<String?>(null)
    private var sourceIntent: Intent? = null

    private lateinit var playerPreferences: ExternalPlayerPreferences
    private lateinit var bridgeMetadataStore: BridgeMetadataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerPreferences = ExternalPlayerPreferences(applicationContext)
        bridgeMetadataStore = BridgeMetadataStore(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerBridgeContent(
                        snapshot = snapshot,
                        players = players,
                        error = error,
                        onSelect = ::forwardToPlayer,
                        onClose = ::finish,
                    )
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(incoming: Intent) {
        sourceIntent = incoming
        snapshot = IntentProbe.inspect(incoming)
        error = null

        if (incoming.action != Intent.ACTION_VIEW || incoming.type?.startsWith("video/") != true) {
            players = emptyList()
            error = "This intent is not a supported video playback request."
            return
        }

        players = queryPlayers(incoming)
        val savedPackage = playerPreferences.targetPackage ?: return
        if (players.any { it.packageName == savedPackage }) {
            forwardToPlayer(savedPackage)
        } else {
            playerPreferences.targetPackage = null
        }
    }

    private fun queryPlayers(incoming: Intent): List<ExternalPlayerOption> {
        val query = Intent(incoming).apply {
            component = null
            selector = null
            setPackage(null)
        }
        return packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == this.packageName) return@mapNotNull null
                ExternalPlayerOption(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(packageManager)?.toString()?.takeIf(String::isNotBlank)
                        ?: packageName,
                )
            }
            .distinctBy(ExternalPlayerOption::packageName)
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun forwardToPlayer(targetPackage: String) {
        val incoming = sourceIntent ?: return
        if (players.none { it.packageName == targetPackage }) {
            error = "The selected player is no longer available."
            return
        }

        val now = System.currentTimeMillis()
        val metadata = IntentPlaybackMetadataExtractor.extract(incoming)
        bridgeMetadataStore.save(targetPackage, metadata, now)
        val forwarded = Intent(incoming).apply {
            component = null
            selector = null
            setPackage(targetPackage)
        }

        runCatching { startActivity(forwarded) }
            .onSuccess {
                playerPreferences.targetPackage = targetPackage
                finish()
            }
            .onFailure {
                bridgeMetadataStore.save(targetPackage, PlaybackMetadata(title = null), now)
                error = it.message ?: "Unable to start the selected player."
            }
    }
}

@Composable
private fun PlayerBridgeContent(
    snapshot: IntentProbeSnapshot?,
    players: List<ExternalPlayerOption>,
    error: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("WatchRelay player bridge", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose the player once. WatchRelay forwards the transient video intent without storing the playback URI and remembers only the player package plus safe content metadata.",
        )

        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

        if (players.isEmpty()) {
            Text("No compatible external video player is available.")
        } else {
            Text("Open with", style = MaterialTheme.typography.titleMedium)
            players.forEach { player ->
                Button(onClick = { onSelect(player.packageName) }) {
                    Text("${player.label} · ${player.packageName}")
                }
            }
        }

        snapshot?.let {
            Text("Sanitized incoming metadata", style = MaterialTheme.typography.titleMedium)
            Text("Action: ${it.action ?: "—"}")
            Text("MIME: ${it.mimeType ?: "—"}")
            Text("Data scheme: ${it.dataScheme ?: "—"}")
            if (it.extras.isEmpty()) {
                Text("Extras: —")
            } else {
                it.extras.forEach { (key, value) -> Text("$key = $value") }
            }
        }

        OutlinedButton(onClick = onClose) {
            Text("Cancel")
        }
    }
}
