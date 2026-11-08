package com.dhalam.ap

import androidx.compose.material3.LinearProgressIndicator

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
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F1E5),
    onPrimaryContainer = Color(0xFF002822),
    secondaryContainer = Color(0xFFD2EDE5),
    onSecondaryContainer = Color(0xFF153D34),
    background = Color(0xFFF4F9F7),
    onBackground = Color(0xFF14211D),
    surface = Color.White,
    onSurface = Color(0xFF14211D),
    onSurfaceVariant = Color(0xFF475952)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FE7D6),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF16483F),
    onPrimaryContainer = Color(0xFFB1F4E6),
    secondaryContainer = Color(0xFF25483E),
    onSecondaryContainer = Color(0xFFD2F0E6),
    background = Color(0xFF101715),
    onBackground = Color(0xFFE5EFEB),
    surface = Color(0xFF1A2521),
    onSurface = Color(0xFFE5EFEB),
    onSurfaceVariant = Color(0xFFB8CCC3)
)

// Material 3 defaults use Android’s built-in Roboto family, including all type roles.
private val DhalamTypography = Typography()

private enum class DashboardTab(val label: String, val iconRes: Int) {
    Triage("Scam check", R.drawable.material_search),
    Access("Access", R.drawable.material_person),
    Permissions("AskDhalam", R.drawable.material_chat),
    Developer("Developer", R.drawable.material_code),
    Tools("Tools", R.drawable.fluent_shield)
}

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
    captureStatus: String = "Unavailable",
    onAnalyze: suspend (org.json.JSONObject) -> org.json.JSONObject = { submitTriage(BuildConfig.ASK_BACKEND_URL, "", it) }
) {
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.Triage.name) }
    var query by rememberSaveable { mutableStateOf("") }

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

    val tab = DashboardTab.valueOf(selectedTab)
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

        if (showExports) {
            AlertDialog(
                onDismissRequest = { showExports = false },
                title = { Text("Export permission report") },
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
                            ) { Text(type.title) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExports = false }) { Text("Close") }
                }
            )
        }

        Scaffold(
            containerColor = colors.background,
            contentColor = colors.onBackground,
            bottomBar = {
                Surface(color = colors.surface, contentColor = colors.onSurface, tonalElevation = 2.dp) {
                    Column(Modifier.imePadding()) {
                        if (tab == DashboardTab.Permissions) {
                            AskDhalamInput(askState, scanning, onQuestionChange, onAsk, onClearAsk, onSignIn, onSignOut)
                        }

                        NavigationBar(
                            containerColor = colors.surface,
                            tonalElevation = 0.dp
                        ) {
                            DashboardTab.values().forEach { destination ->
                                NavigationBarItem(
                                    selected = tab == destination,
                                    onClick = { selectedTab = destination.name },
                                    icon = {
                                        Icon(
                                            painter = painterResource(destination.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    label = { Text(destination.label) }
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
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.dhalam_logo_transparent),
                        contentDescription = "Dhalam logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Caption(lastScan?.let { "Last scan · $it" } ?: "Device review")
                        TextButton(onClick = onSignOut) { Text("Log out") }
                    }
                    Button(onClick = onScan, enabled = !scanning) {
                        Icon(painterResource(R.drawable.material_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        Text(if (scanning) "Scanning…" else "Scan now")
                    }
                }
                if (captureStatus == "Active") {
                    Surface(color = colors.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Coercion Shield · screen capture active", style = MaterialTheme.typography.titleMedium)
                            Text("Pause before viewing private information. Stop sharing using Android or the sharing app. Dhalam cannot disconnect another app’s session.")
                            if (observation?.services?.isNotEmpty() == true) {
                                Text("Accessibility access was also enabled at the last check. Both signals can be legitimate; they do not prove fraud.")
                            }
                        }
                    }
                }
                if (tab == DashboardTab.Permissions) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.material_search), contentDescription = null)
                        },
                        label = { Text("Find an app or permission") },
                        placeholder = { Text("App name, package, camera, location…") },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                TextButton(onClick = { query = "" }) { Text("Clear") }
                            }
                        }
                    )
                }

                if (tab == DashboardTab.Triage) {
                    ScamTriagePanel(Modifier.weight(1f), onAnalyze, onSignIn)
                } else key(tab) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(askState.loading, askState.result, askState.error) {
                        if (askState.loading || askState.result != null || askState.error != null) {
                            listState.animateScrollToItem(0)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (tab == DashboardTab.Permissions &&
                            (askState.loading || askState.error != null || askState.result != null)) {
                            item("ask_answer") {
                                AskAnswerPanel(askState, observation?.observedAt, onAsk)
                            }
                        }
                        if (scanError != null) {
                            item("scan_error") { Text(scanError, color = colors.error) }
                        }

                        item("capture_awareness") {
                            Panel {
                                Heading("Screen capture awareness")
                                Caption(when (captureStatus) {
                                    "Active" -> "Dhalam is visible in a recording. If unintended, stop screen recording or sharing before viewing private reports."
                                    "Inactive" -> "No active capture reported for Dhalam."
                                    else -> "Capture detection unavailable. Requires Android 15 or later and supported system detection."
                                })
                                Caption("While Dhalam is visible only; does not detect every capture method or captures of other apps.")
                            }
                        }
                        when (tab) {
                            DashboardTab.Triage -> item("scam_triage") { ScamTriagePanel(analyze = onAnalyze, onSignIn = onSignIn) }
                            DashboardTab.Tools -> item("security_tools") { SecurityToolsPanel(observation?.observedAt) }
                            DashboardTab.Permissions -> {
                                item("permission_summary") {
                                    Panel(accent = true) {
                                        TextButton(
                                            onClick = { showExports = true },
                                            enabled = inventory != null && !exporting
                                        ) { Heading("Permission report") }
                                        Caption(if (exporting) "Exporting…" else "Tap Permission report to export CSV")
                                        exportMessage?.let { Caption(it) }
                                        Text(
                                            when {
                                                inventory != null ->
                                                    "${inventory.packageCount} apps · " +
                                                            "${inventory.permissions.size} permissions"
                                                scanning -> "Reading permissions…"
                                                else -> "Permission inventory unavailable"
                                            }
                                        )
                                        Caption("Visible apps in this Android profile")
                                    }
                                }

                                if (inventory != null) {
                                    if (search.isEmpty()) items(inventory.permissions, key = { "overview:${it.name}" }) {
                                        PermissionRow(it, "")
                                    }
                                    when {
                                        search.isEmpty() -> item("search_hint") {
                                            Caption("Search by app name, package name, or permission. Grants describe access, not actual use.")
                                        }
                                        matches.isEmpty() -> item("no_matches") {
                                            Caption("No matching apps or permissions.")
                                        }
                                        else -> items(matches, key = { "permission:${it.name}" }) {
                                            PermissionRow(it, search)
                                        }
                                    }
                                }
                            }

                            DashboardTab.Access -> {
                                item("access_summary") {
                                    Panel(accent = true) {
                                        Heading("Accessibility access")
                                        Text(
                                            when {
                                                services == null -> "Enabled service list unavailable"
                                                services.isEmpty() -> "No enabled services reported"
                                                else -> "${services.size} enabled services reported"
                                            }
                                        )
                                        TextButton(onClick = onOpenAccessibility) {
                                            Text("Open Accessibility settings")
                                        }
                                    }
                                }
                                items(services.orEmpty()) { service -> ServiceRow(service) }
                            }

                            DashboardTab.Developer -> {
                                item("device_security") {
                                    Panel {
                                        Heading("Device security")
                                        Caption("Configuration and availability checks, not passcode strength or proof of device safety.")
                                        observation?.deviceSignals.orEmpty().forEach { signal ->
                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                                Text(signal.label)
                                                Caption(signal.status)
                                            }
                                        }
                                    }
                                }
                                item("developer_summary") {
                                    Panel {
                                        Heading("Developer tools")
                                        Caption("Confirm restricted debugging switches in Android Settings.")
                                        observation?.developerSignals.orEmpty().forEach { signal ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Text(signal.label, modifier = Modifier.weight(1f))
                                                Caption(signal.status)
                                            }
                                        }
                                        TextButton(onClick = onOpenDeveloperOptions) {
                                            Text("Open Developer options")
                                        }
                                    }
                                }
                            }
                        }

                        if (!notificationsEnabled) {
                            item("quiet_alerts") {
                                TextButton(onClick = onEnableAlerts) {
                                    Text("Enable quiet alerts")
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
                Caption("${report.grantedCount} granted · ${report.apps.size} requested")
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "View apps")
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
                Column(Modifier.padding(top = 4.dp)) {
                    Text("${app.label} · $status")
                    Caption(app.packageName)
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
                Text(if (expanded) "Hide" else "Details")
            }
        }

        if (expanded) {
            Caption(service.id ?: "Service ID unavailable")
            Caption(
                service.capabilities.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

private fun permissionTitle(name: String): String =
    name.substringAfterLast('.')
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }
