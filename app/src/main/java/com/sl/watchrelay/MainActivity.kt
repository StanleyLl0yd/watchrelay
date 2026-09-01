package com.sl.watchrelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.sl.watchrelay.ui.MvpRepository
import com.sl.watchrelay.ui.WatchRelayScreen

class MainActivity : ComponentActivity() {
    private val resumeVersion = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { MvpRepository(applicationContext) }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WatchRelayScreen(
                        repository = repository,
                        resumeVersion = resumeVersion.intValue,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeVersion.intValue += 1
    }
}
