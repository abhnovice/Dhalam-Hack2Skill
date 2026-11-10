package com.dhalam.ap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Only starts sharing after an on-device button press and runtime permission approval. */
class DeviceReviewCompanionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = com.dhalam.ap.ui.theme.DarkColors,
                typography = com.dhalam.ap.ui.theme.Typography) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 40.dp, vertical = 24.dp)
                        .verticalScroll(rememberScrollState())) {
                        BluetoothReviewPanel(companionOnly = true)
                    }
                }
            }
        }
    }
}
