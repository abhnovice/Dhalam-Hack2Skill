package com.dhalam.ap

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun AskDhalamInput(
    state: AskUiState,
    scanning: Boolean,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onClear: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    // Local attachment drafts only; transport stays text-only until multimodal support is wired.
    var attachments by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        attachments = uris.map { uri ->
            val name = runCatching {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "Attachment"
            uri to name
        }
    }
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = { Text("Attach files") },
            text = {
                Text("In the file picker, use Android’s Back button or swipe inward from the left or right edge to return without selecting a file.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showAttachmentDialog = false
                    attachmentPicker.launch("*/*")
                }) { Text("Choose files") }
            },
            dismissButton = {
                TextButton(onClick = { showAttachmentDialog = false }) { Text("Cancel") }
            }
        )
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.question,
                onValueChange = onQuestionChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                label = { Text("Ask Dhalam") },
                placeholder = { Text("Ask about an app or permission") },
                enabled = !state.loading && !state.signingIn,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!scanning && !state.signingIn && state.signedIn) onAsk() })
            )
            Button(onClick = onAsk, enabled = state.settingsReady && !state.loading &&
                !scanning && !state.signingIn && state.question.isNotBlank() && state.signedIn,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Send") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { showAttachmentDialog = true },
                enabled = !state.loading && !state.signingIn
            ) {
                Icon(painterResource(R.drawable.material_attach), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Attach")
            }
            if (attachments.isNotEmpty()) {
                TextButton(onClick = { attachments = emptyList() }) { Text("Remove attachments") }
            }
        }
        if (attachments.isNotEmpty()) {
            Text(
                attachments.joinToString { it.second },
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Attachments are drafts only. Only your text is sent until multimodal model support is connected.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.signedIn) {
                Text(state.accountLabel ?: "Signed in", modifier = Modifier.weight(1f).padding(top = 12.dp),
                    maxLines = 1, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onSignOut) { Text("Sign out") }
            } else {
                OutlinedButton(onClick = onSignIn, enabled = !state.signingIn && !state.loading) {
                    Text(if (state.signingIn) "Signing in…" else "Sign in with Google")
                }
            }
            if (state.loading || state.result != null || state.error != null || state.question.isNotBlank()) {
                TextButton(onClick = { attachments = emptyList(); onClear() }) {
                    Text(if (state.loading) "Cancel" else "Clear")
                }
            }
        }
        Text("Sending shares your question and selected scan facts with your backend and Google Gemini.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
fun AskAnswerPanel(state: AskUiState, currentScanAt: Long?, onRetry: () -> Unit) {
    var expanded by remember(state.result) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ask Dhalam", style = MaterialTheme.typography.titleMedium)
            state.submittedQuestion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Preparing an answer…")
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = !state.loading) { Text("Retry") }
            }
            state.result?.let { result ->
                Text(result.answer, maxLines = if (expanded) Int.MAX_VALUE else 6)
                Text("Based on ${result.evidenceCount} selected facts · " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.observedAt)),
                    style = MaterialTheme.typography.bodySmall)
                if (currentScanAt != null && currentScanAt != result.observedAt) {
                    Text("A newer scan is available. Send again for an updated answer.",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Details and evidence") }
                if (expanded) {
                    result.findings.forEach { finding ->
                        Text("${finding.title} · ${finding.severity}", style = MaterialTheme.typography.titleSmall)
                        Text(finding.explanation)
                        finding.sources.forEach { fact ->
                            Text("Evidence: ${fact.detail}\n${fact.subjectId} · ${fact.status}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    result.recommendations.forEach { Text("• $it") }
                    result.limitations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text("This is an AI explanation of observed exposure, not proof of a scam. Risk scoring is not available yet.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
