package com.dhalam.ap

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Small, bounded cache. Resolve adaptive icons on IO rather than on the UI thread.
private val appIcons = LruCache<String, ImageBitmap>(64)

@Composable
internal fun InstalledAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(appIcons.get(packageName), packageName) {
        value = appIcons.get(packageName)
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(96, 96).asImageBitmap().also { appIcons.put(packageName, it) }
            } catch (_: PackageManager.NameNotFoundException) { null }
              catch (_: SecurityException) { null }
        }
    }
    val iconModifier = modifier.clip(RoundedCornerShape(12.dp)).testTag("app-icon:${packageName}")
    if (bitmap != null) Image(bitmap!!, contentDescription = null, modifier = iconModifier)
    else Surface(modifier = iconModifier, color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp)) {
        Icon(painterResource(R.drawable.ms_code), contentDescription = null, modifier = Modifier.padding(8.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppCategoryCard(category: String, apps: List<ObservedApp>) {
    var expanded by rememberSaveable(category, apps.firstOrNull()?.launcherVisible) { mutableStateOf(false) }
    var visibleCount by rememberSaveable(category) { mutableIntStateOf(12) }
    var selected by remember { mutableStateOf<ObservedApp?>(null) }
    val colors = MaterialTheme.colorScheme
    selected?.let { app ->
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InstalledAppIcon(app.packageName, Modifier.size(64.dp))
                    Text(app.packageName)
                    Text(trText(app.category))
                    Text(trText(app.categorySource), style = MaterialTheme.typography.bodySmall)
                    Text(trText(if (app.systemApp) "System app" else "Installed app"))
                    Text(trText(when (app.launcherVisible) {
                        true -> "Launcher icon reported"
                        false -> "No launcher icon reported"
                        null -> "Launcher visibility unknown"
                    }))
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(trText("Close")) } })
    }
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(trText(category), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${apps.size}  ${if (expanded) "−" else "+"}")
            }
            if (!expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.take(5).forEach { InstalledAppIcon(it.packageName, Modifier.size(36.dp)) }
                    if (apps.size > 5) Text("+${apps.size - 5}", style = MaterialTheme.typography.labelMedium)
                }
                Text(trText("Tap the category to view apps"), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = when { maxWidth >= 520.dp -> 3; maxWidth < 240.dp -> 1; else -> 2 }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        apps.take(visibleCount).chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { app ->
                                    OutlinedCard(onClick = { selected = app }, modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            InstalledAppIcon(app.packageName, Modifier.size(48.dp))
                                            Text(app.label, style = MaterialTheme.typography.labelLarge,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                            Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                                        }
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                if (apps.size > visibleCount) TextButton(onClick = { visibleCount += 12 }) {
                    Text(trText("Show more apps"))
                }
                Text(trText("Tap an app for its metadata"), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
        }
    }
}
