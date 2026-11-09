package com.dhalam.ap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Local only: typing or inspecting never submits the message. */
@Composable
fun ChatLinkPreview(text: String) {
    val links = remember(text) { Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.distinct().take(5).toList() }
    var open by remember(text) { mutableStateOf(false) }
    if (links.isEmpty()) return
    TextButton(onClick = { open = true }) { Text(trText("Inspect link locally${if (links.size > 1) " (${links.size})" else ""}")) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(trText("Link breakdown")) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(trText("Local check only. No link was opened or sent. Showing up to five distinct HTTP(S) links."))
                links.forEach { link ->
                    val result = remember(link) { inspectSecurityLink(link) }
                    SelectionContainer { Text(trText(link), style = MaterialTheme.typography.bodySmall) }
                    Text(trText("Actual hostname"), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(trText(result.host.ifEmpty { "Unavailable — invalid input" }), style = MaterialTheme.typography.titleMedium) }
                    if (result.host.isNotEmpty()) {
                        Text(trText("Scheme: ${result.scheme} · transport only, not trust"))
                        SelectionContainer { Text(trText("Path: ${result.path}")) }
                    }
                    result.notes.forEach { Text(trText(it)) }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(trText("Close")) } }
    )
}
