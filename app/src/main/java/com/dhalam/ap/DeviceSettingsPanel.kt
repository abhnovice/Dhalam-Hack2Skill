package com.dhalam.ap

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

internal fun compactSettingStatus(value: String): String = when {
    value.startsWith("Not configured") -> "Not set"
    value.startsWith("Configured") -> "Set"
    value.startsWith("Ready") -> "Ready"
    value.startsWith("Not enrolled") -> "Not enrolled"
    value.startsWith("Reported on") -> "On"
    value.startsWith("Reported off") -> "Off"
    value.contains("unavailable", true) || value.contains("unknown", true) -> "Unavailable"
    value == "Check in Settings" -> "Review"
    else -> value.substringBefore(" — ").take(45)
}

@Composable
internal fun DeviceSettingsPanel(
    observation: SecurityObservation?,
    capture: CaptureObservation,
    notificationsEnabled: Boolean,
    onDeveloperSettings: () -> Unit,
    onEnableAlerts: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    val signals = observation?.deviceSignals.orEmpty()
    val pending = signals.count { explainSecurity(it.label, it.status).state != "Configured" }
    detail?.let { (title, text) ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(trText(title)) },
            text = { Text(trText(text)) },
            confirmButton = { TextButton(onClick = { detail = null }) { Text(trText("Close")) } },
            dismissButton = { TextButton(onClick = {
                detail = null
                runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            }) { Text(trText("Open Settings")) } })
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(trText("Device snapshot"), style = MaterialTheme.typography.headlineSmall)
        Text(trText(if (observation == null) "Scan to check this device."
            else if (pending > 0) "$pending device checks need review."
            else "Lock and biometrics checked. Review updates."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingGrid("Device security", signals, { label ->
            when (label) {
                "Device lock" -> R.drawable.ms_lock
                "Biometric authentication" -> R.drawable.ms_fingerprint
                else -> R.drawable.ms_update
            }
        }) { signal -> detail = signal.label to signal.status }
        OutlinedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ms_screen), null, Modifier.size(24.dp))
                    Text(trText("Screen capture"), style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(trText("Recording"), style = MaterialTheme.typography.labelLarge)
                        Text(trText(capture.recording), style = MaterialTheme.typography.bodyLarge,
                            color = if (capture.recording == "Active") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(trText("Screenshot"), style = MaterialTheme.typography.labelLarge)
                        Text(trText(when {
                            capture.lastScreenshotAt != null -> "Detected"
                            capture.screenshotsAvailable -> "Watching"
                            else -> "Unavailable"
                        }), style = MaterialTheme.typography.bodyLarge)
                        capture.lastScreenshotAt?.let {
                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(trText("Dhalam only, while visible. Not all capture methods are detectable."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingGrid("Developer tools", observation?.developerSignals.orEmpty(), { label ->
            if (label == "Wireless debugging") R.drawable.ms_wifi else R.drawable.ms_code
        }) { signal -> detail = signal.label to signal.status }
        TextButton(onClick = onDeveloperSettings) { Text(trText("Open Developer options")) }
        if (!notificationsEnabled) TextButton(onClick = onEnableAlerts) { Text(trText("Enable alerts")) }
    }
}

@Composable
private fun SettingGrid(
    title: String,
    signals: List<DeveloperSignal>,
    icon: (String) -> Int,
    onSelect: (DeveloperSignal) -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(trText(title), style = MaterialTheme.typography.titleMedium)
            if (signals.isEmpty()) Text(trText("No scan yet"), style = MaterialTheme.typography.bodySmall)
            signals.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { signal ->
                        TextButton(onClick = { onSelect(signal) }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(painterResource(icon(signal.label)), contentDescription = null, modifier = Modifier.size(24.dp))
                                Text(trText(signal.label), style = MaterialTheme.typography.labelLarge,
                                    minLines = 2,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(trText(compactSettingStatus(signal.status)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
