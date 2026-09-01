package com.sl.watchrelay.diagnostics

import android.content.Intent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class IntentProbeActivity : ComponentActivity() {
    private var snapshot by mutableStateOf<IntentProbeSnapshot?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        snapshot = IntentProbe.inspect(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProbeContent(snapshot = snapshot, onClose = ::finish)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        snapshot = IntentProbe.inspect(intent)
    }
}

@androidx.compose.runtime.Composable
private fun ProbeContent(snapshot: IntentProbeSnapshot?, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("External-player intent probe", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Diagnostic mode only. WatchRelay does not forward or persist the media URI in this phase.",
        )
        snapshot?.let {
            Text("Action: ${it.action ?: "—"}")
            Text("MIME: ${it.mimeType ?: "—"}")
            Text("Data scheme: ${it.dataScheme ?: "—"}")
            Text("Categories: ${it.categories.joinToString().ifBlank { "—" }}")
            Text("Extras", style = MaterialTheme.typography.titleMedium)
            if (it.extras.isEmpty()) {
                Text("No extras")
            } else {
                it.extras.forEach { (key, value) -> Text("$key = $value") }
            }
        }
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}
