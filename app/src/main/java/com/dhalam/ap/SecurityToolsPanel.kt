package com.dhalam.ap

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SecurityToolsPanel(observedAt: Long?) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var journal by remember { mutableStateOf(SecurityJournalData()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Findings") }
    var url by remember { mutableStateOf("") }
    var inspection by remember { mutableStateOf<LinkInspection?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var clearPrompt by remember { mutableStateOf(false) }
    var expandedFinding by remember { mutableStateOf<String?>(null) }
    var findingFilter by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(observedAt) {
        try {
            journal = withContext(Dispatchers.IO) { SecurityJournalStore.load(context) }
            error = null
        } catch (_: Exception) { error = "Could not read saved history. Existing storage was not overwritten. Clear local history to reset it." }
    }
    fun update(operation: () -> SecurityJournalData) {
        busy = true
        scope.launch {
            try { journal = withContext(Dispatchers.IO) { operation() }; error = null }
            catch (_: Exception) { error = "Could not save this change. Please try again." }
            finally { busy = false }
        }
    }

    if (clearPrompt) AlertDialog(onDismissRequest = { clearPrompt = false },
        title = { Text("Clear local history and review confirmations?") },
        text = { Text("This removes the saved checks and checklist timestamps from Dhalam on this device.") },
        confirmButton = { TextButton(onClick = { clearPrompt = false; preview = null; update { SecurityJournalStore.clear(context) } }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearPrompt = false }) { Text("Cancel") } })
    if (preview != null) AlertDialog(onDismissRequest = { preview = null },
        title = { Text("Report preview") },
        text = { SelectionContainer { Text(preview!!, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(enabled = activity != null, onClick = {
            try { activity?.let { shareSecurityReport(it, preview!!) } }
            catch (_: Exception) { error = "Could not open the share sheet." }
        }) { Text("Share this report") } },
        dismissButton = { TextButton(onClick = { preview = null }) { Text("Close") } })

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Security overview", style = MaterialTheme.typography.headlineSmall)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Findings", "History", "Privacy review", "Link inspector", "Report").forEach { label ->
                FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
            }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        when (tab) {
            "Findings" -> {
                if (journal.records.isEmpty()) Text("Run a scan to see your device review.")
                journal.records.firstOrNull()?.let { record ->
                    FindingsOverview(record.findings, findingFilter) { findingFilter = it }
                    Text("Observed " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(record.at)), style = MaterialTheme.typography.bodySmall)
                    if (findingFilter != null) TextButton(onClick = { findingFilter = null }) { Text("Show all findings") }
                    if (findingFilter != null && record.findings.none { it.state == findingFilter }) Text("No recorded findings in this category.")
                }
                journal.records.firstOrNull()?.findings?.filter { findingFilter == null || it.state == findingFilter }?.forEach { finding ->
                    OutlinedCard(onClick = { expandedFinding = if (expandedFinding == finding.title) null else finding.title }, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(finding.title, style = MaterialTheme.typography.titleSmall)
                                    Text(finding.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (expandedFinding == finding.title) "−" else "+")
                            }
                            if (expandedFinding == finding.title) {
                                Text(finding.detail)
                                Text(finding.action)
                                Text("Last readable: ${journal.lastSuccess(finding.title)?.let(::securityStamp) ?: "Never recorded"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            "History" -> {
                Text("Changes since previous check", style = MaterialTheme.typography.titleMedium)
                Text("Times show when a change was observed, not when it happened.")
                if (journal.records.isEmpty()) Text("No checks recorded yet.")
                Text("Last 30 successful foreground checks, stored locally. Inventory history contains counts, not individual app or permission changes. Times are UTC. Background checks continue separately.")
                journal.records.forEachIndexed { index, record ->
                    Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("● Observed at ${securityStamp(record.at)}", style = MaterialTheme.typography.titleMedium)
                        Text(if (index == journal.records.lastIndex) "First retained observation" else journal.changes(index).first())
                        TextButton(onClick = { expanded = if (expanded == record.at) null else record.at }) { Text(if (expanded == record.at) "Hide details" else "View changes and readings") }
                        if (expanded == record.at) {
                            if (index < journal.records.lastIndex) journal.changes(index).forEach { Text("↔ $it") }
                            HorizontalDivider()
                            record.findings.forEach { Text("${it.title} [${it.state}]: ${it.detail}") }
                        }
                    } }
                }
                TextButton(enabled = !busy, onClick = { clearPrompt = true }) { Text("Clear local history and reviews") }
            }
            "Privacy review" -> {
                Text("Manual review. Confirm only after reviewing the setting. These are your confirmations, not automatic verification; revisit them after changes.")
                securityReviewSteps.forEach { (id, instructions) ->
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(checked = journal.reviewed.containsKey(id), enabled = !busy,
                            onCheckedChange = { checked -> update { SecurityJournalStore.review(context, id, checked) } })
                        Column(Modifier.weight(1f).padding(top = 12.dp)) {
                            Text(id, style = MaterialTheme.typography.titleMedium)
                            Text(instructions)
                            Text(journal.reviewed[id]?.let { "User-confirmed: ${securityStamp(it)}" } ?: "Not confirmed")
                        }
                    }
                }
                TextButton(onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS)) }
                    catch (_: Exception) { error = "Could not open settings. Follow the manual paths above." }
                }) { Text("Open system privacy settings") }
                Text("Menu names vary by Android device.")
            }
            "Link inspector" -> {
                Text("Inspect a pasted link locally. It will not be opened, fetched, stored in history or included in reports.")
                OutlinedTextField(value = url, onValueChange = { if (it.length <= 4096) { url = it; inspection = null } }, label = { Text("URL to inspect") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                Button(onClick = { inspection = inspectSecurityLink(url) }) { Text("Inspect link") }
                inspection?.let { result ->
                    Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Actual hostname", style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(result.host.ifEmpty { "Unavailable — invalid input" }, style = MaterialTheme.typography.titleLarge) }
                        if (result.host.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Scheme: ${result.scheme} · transport only, not trust")
                            SelectionContainer { Text("Path: ${result.path}") }
                        }
                        Text("Inspected locally; link not opened.")
                    } }
                    result.notes.forEach { Text(it) }
                }
            }
            "Report" -> {
                Text("Preview before sharing. Reports include device configuration, check times and manual review confirmations. Choose the recipient in the share sheet.")
                Button(enabled = error == null, onClick = { preview = journal.report() }) { Text("Generate report preview") }
            }
        }
    }
}
