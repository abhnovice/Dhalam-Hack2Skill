package com.dhalam.ap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.question,
                onValueChange = onQuestionChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                label = { Text(trText("Ask Dhalam")) },
                placeholder = { Text(trText("Ask about an app or permission")) },
                enabled = !state.loading && !state.signingIn,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!scanning && !state.signingIn && state.signedIn) onAsk() })
            )
            Button(onClick = onAsk, enabled = state.settingsReady && !state.loading &&
                !scanning && !state.signingIn && state.question.isNotBlank() && state.signedIn,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(trText("Send")) }
        }
        ChatLinkPreview(state.question)
        Text(trText("For scam-pattern analysis of messages, URLs and screenshots, use the Scam check tab."),
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.signedIn) {
                Text(trText(state.accountLabel ?: "Signed in"), modifier = Modifier.weight(1f).padding(top = 12.dp),
                    maxLines = 1, style = MaterialTheme.typography.bodySmall)

            } else {
                OutlinedButton(onClick = onSignIn, enabled = !state.signingIn && !state.loading) {
                    Text(trText(if (state.signingIn) "Signing in…" else "Sign in with Google"))
                }
            }
            if (state.loading || state.result != null || state.error != null || state.question.isNotBlank()) {
                TextButton(onClick = { onClear() }) {
                    Text(trText(if (state.loading) "Cancel" else "Clear"))
                }
            }
        }
        Text(trText("Camera app questions use your local scan. Other questions share your question and selected scan facts with your backend and Google Gemini."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
fun AskAnswerPanel(state: AskUiState, currentScanAt: Long?, onRetry: () -> Unit) {
    var expanded by remember(state.result) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trText("Ask Dhalam"), style = MaterialTheme.typography.titleMedium)
            state.submittedQuestion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(trText("Preparing an answer…"))
            }
            state.error?.let {
                Text(trText(it), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = !state.loading) { Text(trText("Retry")) }
            }
            state.result?.let { result ->
                Text(result.answer, maxLines = if (expanded) Int.MAX_VALUE else 6)
                Text(trText("Based on ${result.evidenceCount} selected facts · " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.observedAt))),
                    style = MaterialTheme.typography.bodySmall)
                if (currentScanAt != null && currentScanAt != result.observedAt) {
                    Text(trText("A newer scan is available. Send again for an updated answer."),
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(trText(if (expanded) "Show less" else "Details and evidence")) }
                if (expanded) {
                    result.findings.forEach { finding ->
                        Text(trText("${finding.title} · ${finding.severity}"), style = MaterialTheme.typography.titleSmall)
                        Text(trText(finding.explanation))
                        finding.sources.forEach { fact ->
                            Text(trText("Evidence: ${fact.detail}\n${fact.subjectId} · ${fact.status}"),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    result.recommendations.forEach { Text(trText("• $it")) }
                    result.limitations.forEach { Text(trText(it), style = MaterialTheme.typography.bodySmall) }
                    Text(trText("This is an AI explanation of observed exposure, not proof of a scam. Risk scoring is not available yet."),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
