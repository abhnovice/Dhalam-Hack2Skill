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
        title = { Text(trText("Clear local history and review confirmations?")) },
        text = { Text(trText("This removes the saved checks and checklist timestamps from Dhalam on this device.")) },
        confirmButton = { TextButton(onClick = { clearPrompt = false; preview = null; update { SecurityJournalStore.clear(context) } }) { Text(trText("Clear")) } },
        dismissButton = { TextButton(onClick = { clearPrompt = false }) { Text(trText("Cancel")) } })
    if (preview != null) AlertDialog(onDismissRequest = { preview = null },
        title = { Text(trText("Report preview")) },
        text = { SelectionContainer { Text(translateSecurityReport(preview!!), modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(enabled = activity != null, onClick = {
            try { activity?.let { shareSecurityReport(it, translateSecurityReport(preview!!)) } }
            catch (_: Exception) { error = "Could not open the share sheet." }
        }) { Text(trText("Share this report")) } },
        dismissButton = { TextButton(onClick = { preview = null }) { Text(trText("Close")) } })

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(trText("Security overview"), style = MaterialTheme.typography.headlineSmall)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Findings", "History", "Link inspector", "Privacy review", "Report").forEach { label ->
                FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(trText(label)) }, modifier = Modifier.padding(end = 8.dp))
            }
        }
        if (error != null) Text(trText(error!!), color = MaterialTheme.colorScheme.error)
        when (tab) {
            "Findings" -> {
                if (journal.records.isEmpty()) Text(trText("Run a scan to see your device review."))
                journal.records.firstOrNull()?.let { record ->
                    FindingsOverview(record.findings, findingFilter) { findingFilter = it }
                    Text(trText("Observed " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(record.at))), style = MaterialTheme.typography.bodySmall)
                    if (findingFilter != null) TextButton(onClick = { findingFilter = null }) { Text(trText("Show all findings")) }
                    if (findingFilter != null && record.findings.none { it.state == findingFilter }) Text(trText("No recorded findings in this category."))
                }
                journal.records.firstOrNull()?.findings?.filter { findingFilter == null || it.state == findingFilter }?.forEach { finding ->
                    OutlinedCard(onClick = { expandedFinding = if (expandedFinding == finding.title) null else finding.title }, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(trText(finding.title), style = MaterialTheme.typography.titleSmall)
                                    Text(trText(finding.state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(trText(if (expandedFinding == finding.title) "−" else "+"))
                            }
                            if (expandedFinding == finding.title) {
                                Text(trText(finding.detail))
                                Text(trText(finding.action))
                                Text(trText("Last readable: ${journal.lastSuccess(finding.title)?.let(::securityStamp) ?: "Never recorded"}"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            "History" -> {
                Text(trText("Changes since previous check"), style = MaterialTheme.typography.titleMedium)
                Text(trText("Times show when a change was observed, not when it happened."))
                if (journal.records.isEmpty()) Text(trText("No checks recorded yet."))
                Text(trText("Last 30 successful foreground checks, stored locally. Inventory history contains counts, not individual app or permission changes. Times are UTC. Background checks continue separately."))
                journal.records.forEachIndexed { index, record ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(trText("● Observed at ${securityStamp(record.at)}"), style = MaterialTheme.typography.titleMedium)
                        Text(trText(if (index == journal.records.lastIndex) "First retained observation" else journal.changes(index).first()))
                        TextButton(onClick = { expanded = if (expanded == record.at) null else record.at }) { Text(trText(if (expanded == record.at) "Hide details" else "View changes and readings")) }
                        if (expanded == record.at) {
                            if (index < journal.records.lastIndex) journal.changes(index).forEach { Text("↔ ${trText(it)}") }
                            HorizontalDivider()
                            record.findings.forEach { Text("${trText(it.title)} [${trText(it.state)}]: ${trText(it.detail)}") }
                        }
                    } }
                }
                TextButton(enabled = !busy, onClick = { clearPrompt = true }) { Text(trText("Clear local history and reviews")) }
            }
            "Privacy review" -> {
                Text(trText("Manual review. Confirm only after reviewing the setting. These are your confirmations, not automatic verification; revisit them after changes."))
                securityReviewSteps.forEach { (id, instructions) ->
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(checked = journal.reviewed.containsKey(id), enabled = !busy,
                            onCheckedChange = { checked -> update { SecurityJournalStore.review(context, id, checked) } })
                        Column(Modifier.weight(1f).padding(top = 12.dp)) {
                            Text(trText(id), style = MaterialTheme.typography.titleMedium)
                            Text(trText(instructions))
                            Text(trText(journal.reviewed[id]?.let { "User-confirmed: ${securityStamp(it)}" } ?: "Not confirmed"))
                        }
                    }
                }
                TextButton(onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS)) }
                    catch (_: Exception) { error = "Could not open settings. Follow the manual paths above." }
                }) { Text(trText("Open system privacy settings")) }
                Text(trText("Menu names vary by Android device."))
            }
            "Link inspector" -> {
                Text(trText("See where a link points without opening it."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = url, onValueChange = { if (it.length <= 4096) { url = it; inspection = null } }, label = { Text(trText("URL to inspect")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium)
                Button(onClick = { inspection = inspectSecurityLink(url) }) { Text(trText("Inspect link")) }
                inspection?.let { result ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(trText("Actual hostname"), style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(trText(result.host.ifEmpty { "Unavailable — invalid input" }), style = MaterialTheme.typography.titleLarge) }
                        if (result.host.isNotEmpty()) {
                            HorizontalDivider()
                            Text(trText("Scheme: ${result.scheme} · transport only, not trust"))
                            SelectionContainer { Text(trText("Path: ${result.path}")) }
                        }
                        Text(trText("Inspected locally; link not opened."))
                    } }
                    val warnings = result.notes.filterNot { it.startsWith("No website was opened") }
                    warnings.forEach { Text(trText(it), style = MaterialTheme.typography.bodyMedium) }
                    var scopeOpen by remember(result) { mutableStateOf(false) }
                    TextButton(onClick = { scopeOpen = !scopeOpen }) { Text(trText(if (scopeOpen) "Hide limitations" else "What this check covers")) }
                    if (scopeOpen) Text(trText("Checks link structure only. The website, redirects, reputation and page content are not checked. Links are not stored in history or reports. HTTPS does not prove trust."), style = MaterialTheme.typography.bodySmall)
                }
            }
            "Report" -> {
                Text(trText("Preview before sharing. Reports include device configuration, check times and manual review confirmations. Choose the recipient in the share sheet."))
                Button(enabled = error == null, onClick = { preview = journal.report() }) { Text(trText("Generate report preview")) }
            }
        }
    }
}
