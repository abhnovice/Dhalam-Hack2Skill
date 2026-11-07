package com.dhalam.ap

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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

private val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold)
)

private val DhalamTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = PlexSans),
        titleMedium = base.titleMedium.copy(fontFamily = PlexSans),
        bodyLarge = base.bodyLarge.copy(fontFamily = PlexSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = PlexSans),
        bodySmall = base.bodySmall.copy(fontFamily = PlexSans),
        labelLarge = base.labelLarge.copy(fontFamily = PlexSans),
        labelMedium = base.labelMedium.copy(fontFamily = PlexSans),
        labelSmall = base.labelSmall.copy(fontFamily = PlexSans)
    )
}

private enum class DashboardTab(val label: String, val iconRes: Int) {
    Permissions("Permissions", R.drawable.fluent_shield),
    Access("Access", R.drawable.fluent_person),
    Developer("Developer", R.drawable.fluent_code)
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
    onOpenDeveloperOptions: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.Permissions.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var question by remember { mutableStateOf("") }

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
                Surface(color = colors.surface, contentColor = colors.onSurface) {
                    Column(Modifier.imePadding()) {
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            label = { Text("Ask Dhalam · preview") },
                            placeholder = { Text("Could this request be a scam?") }
                        )

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
                    Icon(
                        painter = painterResource(R.drawable.fluent_shield),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Dhalam",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Caption(lastScan?.let { "Last scan · $it" } ?: "Device review")
                    }
                    Button(onClick = onScan, enabled = !scanning) {
                        Text(if (scanning) "Scanning…" else "Scan now")
                    }
                }

                key(tab) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (scanError != null) {
                            item("scan_error") { Text(scanError, color = colors.error) }
                        }

                        when (tab) {
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
                                    item("permission_search") {
                                        OutlinedTextField(
                                            value = query,
                                            onValueChange = { query = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            label = { Text("Find an app or permission") },
                                            placeholder = { Text("App name, package, camera, location…") },
                                            trailingIcon = {
                                                if (query.isNotEmpty()) {
                                                    TextButton(onClick = { query = "" }) {
                                                        Text("Clear")
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    when {
                                        search.isEmpty() -> item("search_hint") {
                                            Caption("Search by app name, package name, or permission.")
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
        shape = RoundedCornerShape(18.dp),
        color = if (accent) colors.primaryContainer else colors.surface,
        contentColor = if (accent) colors.onPrimaryContainer else colors.onSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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