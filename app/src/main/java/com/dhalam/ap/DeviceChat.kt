package com.dhalam.ap

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Session-only transcript. Answers are based on current scan facts, never invented device history. */
@Composable
internal fun DeviceChat(
    state: AskUiState,
    scanning: Boolean,
    currentScanAt: Long?,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onClear: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val turns = state.conversation.ifEmpty {
        state.result?.let { listOf(AskChatTurn(state.submittedQuestion.orEmpty(), it)) }.orEmpty()
    }
    LaunchedEffect(turns.size, state.loading, state.error) {
        if (scroll.layoutInfo.totalItemsCount > 0) scroll.animateScrollToItem(scroll.layoutInfo.totalItemsCount - 1)
    }
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("device-chat")) {
        Column(Modifier.fillMaxWidth().height(520.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(R.drawable.google_g), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(trText("Google Gemini"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text(trText("New chat")) }
            }
            LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (turns.isEmpty() && !state.loading && state.error == null) item("welcome") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.dhalam_logo_transparent), "Dhalam logo", Modifier.size(64.dp))
                        Text(trText("What would you like to understand?"), style = MaterialTheme.typography.titleLarge)
                        Text(trText("Ask about apps or permissions."),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        SuggestionChip(onClick = { onQuestionChange("Which apps have microphone access?") },
                            label = { Text(trText("Who can use my microphone?")) })
                    }
                }
                turns.forEachIndexed { index, turn ->
                    item("turn:$index") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuestionBubble(turn.question)
                            AssistantBubble(turn.result, currentScanAt)
                        }
                    }
                }
                if (state.loading || state.error != null) item("pending") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.submittedQuestion?.let { QuestionBubble(it) }
                        if (state.loading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(trText("Reviewing your scan…"), style = MaterialTheme.typography.bodyMedium)
                        }
                        state.error?.let {
                            Text(trText(it), color = colors.error)
                            TextButton(onClick = onAsk, enabled = !scanning && state.question.isNotBlank()) { Text(trText("Retry")) }
                        }
                    }
                }
            }
            HorizontalDivider()
            AskDhalamInput(state, scanning, onQuestionChange, onAsk, onClear, onSignIn, onSignOut)
        }
    }
}

@Composable
private fun QuestionBubble(question: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(start = 28.dp)) {
            Text(question, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AssistantBubble(result: AskResult, currentScanAt: Long?) {
    var details by remember(result) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.dhalam_logo_transparent), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(trText("Dhalam"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(result.answer)
        Text(trText("${result.evidenceCount} scan facts"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (currentScanAt != null && currentScanAt != result.observedAt) Text(
            trText("A newer scan is available. Ask again for an updated answer."),
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { details = true }) { Text(trText("Details and evidence")) }
    }
    if (details) AlertDialog(onDismissRequest = { details = false },
        title = { Text(trText("Details and evidence")) },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    result.findings.forEach { finding ->
                        Text(finding.title, style = MaterialTheme.typography.titleMedium)
                        Text(finding.explanation)
                        finding.sources.forEach { Text("${it.detail}\n${it.status}", style = MaterialTheme.typography.bodySmall) }
                    }
                    result.recommendations.forEach { Text("• $it") }
                    result.limitations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }, confirmButton = { TextButton(onClick = { details = false }) { Text(trText("Close")) } })
}
