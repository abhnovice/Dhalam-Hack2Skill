package com.dhalam.ap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AccessOverview(
    observation: SecurityObservation?,
    scanning: Boolean,
    scanError: String?,
    onOpenSettings: () -> Unit,
    onReport: suspend (SecurityObservation) -> String,
    modifier: Modifier = Modifier
) {
    var visibleAppsView by rememberSaveable { mutableStateOf(false) }
    var nonLauncherAppsView by rememberSaveable { mutableStateOf(false) }
    var settingsView by rememberSaveable { mutableStateOf(true) }
    var consent by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var settingDetail by remember { mutableStateOf<AccessibilitySetting?>(null) }
    var report by rememberSaveable { mutableStateOf<String?>(null) }
    var reportAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val latestAt by rememberUpdatedState(observation?.observedAt)
    val colors = MaterialTheme.colorScheme
    val settingGroups = remember(observation?.accessibilitySettings) {
        observation?.accessibilitySettings.orEmpty().groupBy { it.group }
    }
    val appGroups = remember(observation?.apps) {
        observation?.apps.orEmpty().groupBy { it.launcherVisible }.mapValues { (_, apps) -> apps.groupBy { it.category }.toSortedMap() }
    }
    if (reportOpen && report != null) AlertDialog(
        onDismissRequest = { reportOpen = false }, title = { Text(trText("Accessibility report")) },
        text = { Text(report!!) },
        confirmButton = { TextButton(onClick = { reportOpen = false }) { Text(trText("Close")) } })
    settingDetail?.let { setting ->
        AlertDialog(onDismissRequest = { settingDetail = null }, title = { Text(trText(setting.label)) },
            text = { Text(trText(setting.status + "\n\n" + setting.explanation)) },
            confirmButton = { TextButton(onClick = { settingDetail = null }) { Text(trText("Close")) } })
    }
    if (consent) AlertDialog(
        onDismissRequest = { consent = false },
        title = { Text(trText("Prepare your accessibility report?")) },
        text = { Text(trText("Send the latest accessibility settings and enabled service names/capabilities to your backend and Google Gemini for a 50–100 word explanation. Your app inventory stays on this phone. AI can be wrong; no settings will be changed.")) },
        confirmButton = {
            TextButton(onClick = {
                consent = false
                val snapshot = observation ?: return@TextButton
                busy = true; failure = null
                scope.launch {
                    try {
                        val answer = onReport(snapshot)
                        if (latestAt == snapshot.observedAt) {
                            report = answer; reportAt = snapshot.observedAt
                        } else {
                            failure = "A newer scan is available. Generate a report for the latest settings."
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: AskSignInRequiredException) { failure = "Link your Google account to prepare a report." }
                    catch (error: java.io.IOException) { failure = connectionFailureMessage(BuildConfig.ASK_BACKEND_URL, error) }
                    catch (error: Exception) { failure = error.message ?: "The report is unavailable. Local settings still work." }
                    finally { busy = false }
                }
            }) { Text(trText("Generate report")) }
        },
        dismissButton = { TextButton(onClick = { consent = false }) { Text(trText("Not now")) } }
    )
    LazyColumn(
        modifier.fillMaxWidth().testTag("access-list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item("intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(trText("Your accessibility overview"), style = MaterialTheme.typography.headlineSmall)
                Text(trText("Settings & app access."),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        item("ai_report") {
            ElevatedCard(shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colors.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(trText("Gemini · accessibility report"), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    val current = reportAt == observation?.observedAt
                    if (report != null && current) {
                        Text(report!!, color = colors.onPrimaryContainer, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { reportOpen = true }) { Text(trText("Read report")) }
                        Text(trText("AI can be wrong. Verify unfamiliar access."),
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(trText(if (report != null) "Your scan has changed. Generate an updated report."
                        else "Explain this scan with Gemini."),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(trText("Reviewing…"), style = MaterialTheme.typography.bodyMedium)
                    }
                    failure?.let { Text(trText(it), color = colors.error) }
                    FilledTonalButton(onClick = { consent = true },
                        enabled = !busy && !scanning && observation?.accessibilitySettings?.isNotEmpty() == true) {
                        Text(trText(if (report == null && failure == null) "Explain my settings" else "Generate updated report"))
                    }
                }
            }
        }
        scanError?.let { item("error") { Text(trText(it), color = colors.error) } }
        item("settings_toggle") {
            OutlinedCard(shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(trText("Accessibility settings view"), style = MaterialTheme.typography.titleMedium)
                        Text(trText("Settings & services"), style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = settingsView, onCheckedChange = { settingsView = it },
                        modifier = Modifier.semantics { contentDescription = "Accessibility settings view" })
                }
            }
        }
        item("apps_toggle") {
            OutlinedCard(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(trText("Apps View"), style = MaterialTheme.typography.titleLarge)
                    AppGroupSwitch("Apps you see", "Apps with launcher icons", visibleAppsView) { visibleAppsView = it }
                    AppGroupSwitch("Apps you don't see", "Apps without launcher icons", nonLauncherAppsView) { nonLauncherAppsView = it }
                    Text(trText("Visible packages only. No launcher icon does not mean harmful."),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(trText("Categories may be approximate. App lists stay on device."),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        if (settingsView) {
        item("settings_heading") {
            Text(trText("Accessibility settings"), style = MaterialTheme.typography.titleLarge)
            Text(trText("Read-only. Tap a setting for details."),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            TextButton(onClick = onOpenSettings) { Text(trText("Open all Accessibility settings")) }
            if (observation == null) Text(trText("Run a scan to view the available settings."))
        }
        settingGroups.forEach { (group, settings) ->
            item("settings:$group") { AccessibilitySettingsCard(group, settings) { settingDetail = it } }
        }
        item("services") {
            AccessServicesCard(observation?.services, observation?.installedServices)
        }
        }
        if (visibleAppsView || nonLauncherAppsView) {
            if (observation?.apps == null) item("apps_unavailable") {
                Text(trText("App inventory unavailable. Scan again to retry."))
            } else {
                listOf(true to "Apps you see", false to "Apps you don't see", null to "Launcher visibility unknown").forEach { (visible, title) ->
                    val groups = appGroups[visible].orEmpty()
                    val showGroup = when (visible) {
                        true -> visibleAppsView
                        false -> nonLauncherAppsView
                        null -> groups.isNotEmpty()
                    }
                    if (showGroup) {
                        item("app_heading:$visible") {
                            Text(trText(title), modifier = Modifier.testTag("apps-heading:$visible"), style = MaterialTheme.typography.titleLarge)
                            Text(trText("${groups.values.sumOf { it.size }} apps reported"),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            if (groups.isEmpty()) Text(trText("No apps reported in this group."))
                        }
                        items(groups.entries.toList(), key = { "app:$visible:${it.key}" }) { (category, apps) ->
                            AppCategoryCard(category, apps)
                        }
                    }
                }
            }
        }
        item("coverage") {
            Text(trText("Some options require review in Android Settings."),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppGroupSwitch(label: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(trText(label), style = MaterialTheme.typography.titleMedium)
            Text(trText(subtitle), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun AccessibilitySettingsCard(group: String, settings: List<AccessibilitySetting>, onSelect: (AccessibilitySetting) -> Unit) {
    var expanded by rememberSaveable(group) { mutableStateOf(group == "Interaction & calls") }
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(trText(group), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "−" else "+")
            }
            Text(trText("${settings.size} settings · ${settings.count { it.status == "Check in Settings" }} need manual review"),
                style = MaterialTheme.typography.bodySmall)
            SlidePushVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    settings.forEach { setting ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(setting) }.padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(trText(setting.label), style = MaterialTheme.typography.titleSmall)
                            Text(trText(compactSettingStatus(setting.status)), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessServicesCard(enabled: List<ObservedAccessibilityService>?, installed: List<ObservedAccessibilityService>?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val enabledIds = enabled.orEmpty().mapNotNull { normalizedServiceId(it.id) }.toSet()
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(trText("Accessibility services"), style = MaterialTheme.typography.titleLarge)
            Text(trText(if (enabled == null) "Enabled service list unavailable"
                else "${enabled.size} enabled services reported"))
            enabled.orEmpty().forEach { service ->
                Text(service.name, style = MaterialTheme.typography.titleMedium)
                Text(trText(service.capabilities.joinToString(" · ").ifBlank { "No checked capabilities reported." }),
                    style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(trText(if (expanded) "Hide installed services" else "View installed services"))
            }
            if (expanded) {
                Text(trText("Installed does not mean enabled."), style = MaterialTheme.typography.bodySmall)
                if (installed == null) Text(trText("Installed service list unavailable"))
                else if (installed.isEmpty()) Text(trText("No installed services reported"))
                installed.orEmpty().forEach { service ->
                    val id = normalizedServiceId(service.id)
                    Text(service.name, style = MaterialTheme.typography.titleSmall)
                    Text(trText(when {
                        id == null || enabled == null -> "Enabled status unknown"
                        id in enabledIds -> "Reported enabled"
                        else -> "Not reported enabled"
                    }), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

