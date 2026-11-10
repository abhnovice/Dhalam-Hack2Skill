package com.dhalam.ap

import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.layout.height
import com.dhalam.ap.ui.theme.LightColors
import com.dhalam.ap.ui.theme.DarkColors
import com.dhalam.ap.ui.theme.Typography as DhalamTypography

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class DashboardTab(val label: String, val iconRes: Int) {
    Triage("Scam check", R.drawable.ms_search),
    Access("Access", R.drawable.ms_person),
    Permissions("Ask Dhalam", R.drawable.ms_chat),
    Developer("Settings", R.drawable.ms_code),
    Tools("Tools", R.drawable.ms_shield)
}

private fun DashboardTab.displayLabel(): String =
    if (appLanguage.value.code == "en") label else tr("Bottom tab: $label")

@Composable
fun SecurityDashboard(
    observation: SecurityObservation?,
    scanning: Boolean,
    scanError: String?,
    notificationsEnabled: Boolean,
    onScan: () -> Unit,
    onEnableAlerts: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    askState: AskUiState,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onClearAsk: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onAccessReport: suspend (SecurityObservation) -> String = { error("Accessibility report is not configured.") },
    captureStatus: String = "Unavailable",
    onAnalyze: suspend (org.json.JSONObject) -> org.json.JSONObject = { submitTriage(BuildConfig.ASK_BACKEND_URL, "", it) },
    captureObservation: CaptureObservation = CaptureObservation(recording = captureStatus),
    onRemoteReport: suspend (RemoteDeviceInventory) -> String = { error("Sign in to use Gemini.") }
) {
    val sectionState = rememberSaveableStateHolder()
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.Triage.name) }
    val pagerState = rememberPagerState(initialPage = DashboardTab.valueOf(selectedTab).ordinal,
        pageCount = { DashboardTab.values().size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { selectedTab = DashboardTab.values()[it].name }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var permissionMenuOpen by remember { mutableStateOf(false) }
    var askOpen by rememberSaveable { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }

    var showExports by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val path = pendingExport
        pendingExport = null
        if (path != null) {
            exporting = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val source = File(path)
                        try {
                            if (uri != null) {
                                requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                                    .use { output -> source.inputStream().use { it.copyTo(output) } }
                            }
                        } finally {
                            source.delete()
                        }
                    }
                    if (uri != null) exportMessage = "Report exported."
                } catch (_: Exception) {
                    exportMessage = "Could not export the report. Please try again."
                } finally {
                    exporting = false
                }
            }
        }
    }

    val tab = DashboardTab.values()[pagerState.currentPage]
    val inventory = observation?.permissionInventory
    val services = observation?.services
    val search = query.trim()
    val matches = remember(inventory, search) {
        if (search.isEmpty()) {
            emptyList()
        } else {
            inventory?.permissions.orEmpty().filter { report ->
                report.name.contains(search, ignoreCase = true) ||
                        permissionTitle(report.name).contains(search, ignoreCase = true) ||
                        report.apps.any { app ->
                            app.label.contains(search, ignoreCase = true) ||
                                    app.packageName.contains(search, ignoreCase = true)
                        }
            }
        }
    }
    val lastScan = remember(observation?.observedAt) {
        observation?.let {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.observedAt))
        }
    }

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = DhalamTypography
    ) {
        val colors = MaterialTheme.colorScheme

        if (accountOpen) AlertDialog(onDismissRequest = { accountOpen = false },
            title = { Text(trText("Your account")) }, text = { Text(trText(askState.accountLabel ?: "Dhalam session")) },
            confirmButton = { TextButton(onClick = { accountOpen = false; onSignOut() }) { Text(trText("Sign out")) } },
            dismissButton = {
                Row {
                    if (askState.localSignIn || askState.temporarySignIn) {
                        TextButton(onClick = { accountOpen = false; onSignIn() }) {
                            Text(trText("Google account"))
                        }
                    }
                    TextButton(onClick = { accountOpen = false }) { Text(trText("Close")) }
                }
            })
        if (showExports) {
            AlertDialog(
                onDismissRequest = { showExports = false },
                title = { Text(trText("Export permission report")) },
                text = {
                    Column {
                        Caption("Exports all visible app permissions from the latest scan. Timestamps indicate observation time, not permission use.")
                        PermissionExport.values().forEach { type ->
                            TextButton(
                                enabled = inventory != null && !exporting,
                                onClick = {
                                    val snapshot = observation ?: return@TextButton
                                    showExports = false
                                    exporting = true
                                    scope.launch {
                                        try {
                                            pendingExport = withContext(Dispatchers.IO) {
                                                val file = File.createTempFile("permission-export-", ".csv", context.cacheDir)
                                                try {
                                                    file.writeText(snapshot.permissionCsv(type), Charsets.UTF_8)
                                                    file.absolutePath
                                                } catch (error: Exception) {
                                                    file.delete()
                                                    throw error
                                                }
                                            }
                                            exportLauncher.launch(type.fileName)
                                        } catch (_: Exception) {
                                            exportMessage = "Could not prepare the report. Please try again."
                                        } finally {
                                            exporting = false
                                        }
                                    }
                                }
                            ) { Text(trText(type.title)) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExports = false }) { Text(trText("Close")) }
                }
            )
        }

        Scaffold(
            containerColor = colors.background,
            contentColor = colors.onBackground,
            bottomBar = {
                Surface(color = colors.surface, contentColor = colors.onSurface, tonalElevation = 2.dp) {
                    Column(Modifier.imePadding()) {

                        NavigationBar(
                            containerColor = colors.surface,
                            tonalElevation = 0.dp
                        ) {
                            DashboardTab.values().forEach { destination ->
                                NavigationBarItem(
                                    colors = NavigationBarItemDefaults.colors(selectedTextColor = colors.primary, selectedIconColor = colors.primary, indicatorColor = colors.secondaryContainer, unselectedTextColor = colors.onSurfaceVariant),
                                    selected = tab == destination,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(destination.ordinal,
                                        animationSpec = tween(340, easing = FastOutSlowInEasing)) } },
                                    icon = {
                                        Icon(
                                            painter = painterResource(destination.iconRes),
                                            contentDescription = trText(destination.label),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    label = { Text(destination.displayLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }
            }
        ) { insets ->
            Column(
                Modifier.fillMaxSize()
                    .padding(insets)
                    .consumeWindowInsets(insets)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.dhalam_logo_transparent),
                            contentDescription = "Dhalam logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(trText("Dhalam"), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Caption(lastScan?.let { "Checked $it" } ?: "Device review")
                        }
                        LanguageMenuButton()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { accountOpen = true }) { Text(trText("Account")) }
                        Button(onClick = onScan, enabled = !scanning) {
                            Icon(painterResource(R.drawable.ms_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                            Text(trText(if (scanning) "Checking…" else "Scan"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (captureObservation.recording == "Active") {
                    Surface(color = colors.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(trText("Coercion Shield · screen capture active"), style = MaterialTheme.typography.titleMedium)
                            Text(trText("Recording Dhalam. Stop sharing before viewing private data."))
                            if (observation?.services?.isNotEmpty() == true) {
                                Text(trText("Accessibility services are also enabled. Review if unfamiliar."))
                            }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds().testTag("dashboard-pager")
                ) { page ->
                    val displayedTab = DashboardTab.values()[page]
                    sectionState.SaveableStateProvider(displayedTab.name) {
                        Column(Modifier.fillMaxSize()) {
                if (displayedTab == DashboardTab.Triage) {
                    ScamTriagePanel(Modifier.weight(1f), onAnalyze, onSignIn)
                } else if (displayedTab == DashboardTab.Access) {
                    AccessOverview(observation, scanning, scanError, onOpenAccessibility, onAccessReport, Modifier.weight(1f))
                } else key(displayedTab) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(askState.loading, askState.result, askState.error) {
                        if (askState.loading || askState.result != null || askState.error != null) {
                            if (displayedTab == DashboardTab.Permissions) askOpen = true
                            listState.animateScrollToItem(0)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("dashboard-list:${displayedTab.name}"),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (scanError != null) {
                            item("scan_error") { Text(trText(scanError), color = colors.error) }
                        }

                        when (displayedTab) {
                            DashboardTab.Triage -> item("scam_triage") { ScamTriagePanel(analyze = onAnalyze, onSignIn = onSignIn) }
                            DashboardTab.Tools -> item("security_tools") { SecurityToolsPanel(observation?.observedAt, captureObservation, onRemoteReport) }
                            DashboardTab.Permissions -> {
                                item("device_chat") {
                                    OutlinedCard(onClick = { askOpen = !askOpen },
                                        modifier = Modifier.fillMaxWidth().testTag("device-chat-toggle"),
                                        colors = CardDefaults.outlinedCardColors(containerColor = colors.surface)) {
                                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Image(painterResource(R.drawable.dhalam_logo_transparent), null, Modifier.size(36.dp))
                                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(trText("Ask Dhalam"), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                                                Caption("Apps & permissions")
                                            }
                                            Text(if (askOpen) "−" else "+", style = MaterialTheme.typography.titleLarge, color = colors.primary)
                                        }
                                    }
                                    SlidePushVisibility(visible = askOpen) {
                                        DeviceChat(askState, scanning, observation?.observedAt,
                                            onQuestionChange, onAsk, onClearAsk, onSignIn, onSignOut)
                                    }
                                }
                                item("permission_heading") {
                                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(trText("Device access"), style = MaterialTheme.typography.headlineSmall)
                                        Caption("Review sensitive access.")
                                    }
                                }
                                if (inventory != null && search.isEmpty()) item("permission_chart") {
                                    SensitivePermissionChart(inventory) { query = it }
                                }
                                item("permission_search") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(trText("Find an app"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                            androidx.compose.foundation.layout.Box {
                                                TextButton(onClick = { permissionMenuOpen = true }) { Text(trText("More")) }
                                                DropdownMenu(expanded = permissionMenuOpen, onDismissRequest = { permissionMenuOpen = false }) {
                                                    DropdownMenuItem(text = { Text(trText("Export permission report")) }, enabled = inventory != null && !exporting,
                                                        onClick = { permissionMenuOpen = false; showExports = true })
                                                }
                                            }
                                        }
                                        OutlinedTextField(value = query, onValueChange = { query = it },
                                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                                            shape = RoundedCornerShape(16.dp), placeholder = { Text(trText("App name or permission")) },
                                            leadingIcon = { Icon(painterResource(R.drawable.ms_search), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(trText("Clear")) } })
                                        exportMessage?.let { Caption(it) }
                                    }
                                }

                                if (inventory != null) {
                                    when {
                                        search.isEmpty() -> Unit
                                        matches.isEmpty() -> item("no_matches") {
                                            Caption("No matching apps or permissions.")
                                        }
                                        else -> items(matches, key = { "permission:${it.name}" }) {
                                            PermissionRow(it, search)
                                        }
                                    }
                                }
                            }

                            DashboardTab.Access -> Unit // Dedicated accessibility overview above.

                            DashboardTab.Developer -> item("settings_overview") {
                                DeviceSettingsPanel(observation, captureObservation, notificationsEnabled,
                                    onOpenDeveloperOptions, onEnableAlerts)
                            }
                        }


                    }
                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(report: PermissionReport, search: String) {
    val permissionMatches = report.name.contains(search, ignoreCase = true) ||
            permissionTitle(report.name).contains(search, ignoreCase = true)
    val visibleApps = if (permissionMatches) report.apps else report.apps.filter {
        it.label.contains(search, ignoreCase = true) ||
                it.packageName.contains(search, ignoreCase = true)
    }
    var expanded by rememberSaveable(report.name, search) { mutableStateOf(!permissionMatches) }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(permissionTitle(report.name))
                Caption("${report.apps.size} apps requested access")
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(trText(if (expanded) "Hide" else "View apps"))
            }
        }

        val distinctApps = report.apps.distinctBy { it.packageName }
        val grantedApps = report.apps.filter { it.granted == true }.distinctBy { it.packageName }.size
        Caption("$grantedApps visible apps with permission · granted access, not actual use")
        LinearProgressIndicator(progress = { if (distinctApps.isEmpty()) 0f else grantedApps.toFloat() / distinctApps.size }, modifier = Modifier.fillMaxWidth())
        if (expanded) {
            Caption(report.name)
            visibleApps.forEach { app ->
                val status = when (app.granted) {
                    true -> "Granted"
                    false -> "Not granted"
                    null -> "Unknown"
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InstalledAppIcon(app.packageName, Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${app.label} · ${trText(status)}")
                        Caption(app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(service: ObservedAccessibilityService) {
    var expanded by rememberSaveable(service.id, service.name) { mutableStateOf(false) }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(service.name)
                Caption("Reported enabled")
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(trText(if (expanded) "Hide" else "Details"))
            }
        }

        if (expanded) {
            Caption(service.id ?: "Service ID unavailable")
            Caption(
                service.capabilities.takeIf { it.isNotEmpty() }?.joinToString(" · ") { trText(it) }
                    ?: "No checked capabilities reported."
            )
        }
    }
}

@Composable
private fun Panel(accent: Boolean = false, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = if (accent) 0.dp else 1.dp,
        color = if (accent) colors.primaryContainer else colors.surface,
        contentColor = if (accent) colors.onPrimaryContainer else colors.onSurface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = trText(text),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Caption(text: String) {
    Text(trText(text), style = MaterialTheme.typography.bodySmall)
}

private fun permissionTitle(name: String): String =
    name.substringAfterLast('.')
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }
