package com.dhalam.ap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val sensitivePermissions = linkedMapOf(
    "Camera" to "android.permission.CAMERA",
    "Microphone" to "android.permission.RECORD_AUDIO",
    "Location" to "android.permission.ACCESS_FINE_LOCATION",
    "Contacts" to "android.permission.READ_CONTACTS",
    "Messages" to "android.permission.READ_SMS"
)

@Composable
fun SensitivePermissionChart(inventory: PermissionInventory, onSelect: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(trText("Sensitive permissions"), style = MaterialTheme.typography.titleMedium)
            Text(trText("${inventory.packageCount} visible apps · tap to explore"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sensitivePermissions.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (label, permission) ->
                        val report = inventory.permissions.firstOrNull { it.name == permission }
                        val count = report?.apps?.filter { it.granted == true }?.map { it.packageName }?.distinct()?.size
                        SensitivePermissionItem(label, permission, count,
                            Modifier.weight(1f)) { onSelect(permission) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(trText("Granted access does not mean actual use."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SensitivePermissionItem(label: String, permission: String, count: Int?,
    modifier: Modifier, onClick: () -> Unit) {
    val icon = when (label) {
        "Camera" -> R.drawable.ms_camera
        "Microphone" -> R.drawable.ms_mic
        "Location" -> R.drawable.ms_location
        "Contacts" -> R.drawable.ms_contacts
        else -> R.drawable.ms_messages
    }
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
        modifier = modifier.testTag("sensitive:$permission")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(painterResource(icon), contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(trText(label), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(trText(count?.let { if (it == 1) "1 app" else "$it apps" } ?: "Not observed"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FindingsOverview(findings: List<SecurityFinding>, selected: String?, onSelect: (String?) -> Unit) {
    val states = (listOf("Needs attention", "Configured", "Manual review", "Unavailable") + findings.map { it.state }).distinct()
    val colors = listOf(Color(0xFF9A5B00), Color(0xFF00796B), Color(0xFF54746B), Color(0xFF9CAFA7))
    val counts = states.map { state -> findings.count { it.state == state } }
    val track = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp).semantics { contentDescription = tr("{n} recorded checks; breakdown follows", mapOf("n" to findings.size)) }) {
                if (findings.isEmpty()) drawArc(track, 0f, 360f, false, style = Stroke(12.dp.toPx()))
                var start = -90f
                counts.forEachIndexed { index, count ->
                    val sweep = if (findings.isEmpty()) 0f else 360f * count / findings.size
                    if (sweep > 0) drawArc(colors[index % colors.size], start, sweep - minOf(3f, sweep / 2), false, style = Stroke(12.dp.toPx()))
                    start += sweep
                }
            }
            Text(trText(findings.size.toString()), style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(trText("Your device review"), style = MaterialTheme.typography.titleMedium)
            Text(trText("${counts.first()} need attention"), style = MaterialTheme.typography.bodyLarge)
            Text(trText("Recorded checks, not a safety score"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    states.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { state ->
                val chosen = selected == state
                OutlinedCard(onClick = { onSelect(if (chosen) null else state) }, modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(trText(findings.count { it.state == state }.toString()), style = MaterialTheme.typography.titleLarge)
                        Text(trText(state), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
