package com.dhalam.ap

import android.app.Activity
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Input exceeds size limit." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun triageOrigin(value: String, debug: Boolean): String {
    val uri = URI(value.trim())
    require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
        (uri.port == -1 || uri.port in 1..65535) &&
        (uri.scheme == "https" || (debug && uri.scheme == "http" &&
            uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")))) {
        "Use an HTTPS backend origin, or a local HTTP origin in a debug build."
    }
    return value.trim().trimEnd('/')
}

internal suspend fun submitTriage(origin: String, token: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
    require(token.isBlank() || origin.startsWith("https://")) { "Cloud tokens require HTTPS." }
    val connection = URL("${triageOrigin(origin, BuildConfig.DEBUG)}/api/v1/analyze-threat").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 75_000
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        if (connection.responseCode == 401) throw AskSignInRequiredException()
        check(connection.responseCode == 200) {
            when (connection.responseCode) {
                403 -> "This Google account is not authorized. Sign in with an allowed, verified account."
                413, 422 -> "Review consent and use a valid message, HTTP(S) URL or image under 4 MiB."
                504 -> "Analysis timed out. Try again later; local checks still work."
                else -> "Cloud analysis is unavailable. Please try again later."
            }
        }
        val bytes = connection.inputStream.use { it.readBounded(100_000) }
        check(bytes.size <= 100_000) { "Unexpected server response." }
        JSONObject(bytes.toString(Charsets.UTF_8)).also {
            check(it.getInt("scam_probability") in 0..100 &&
                it.getString("threat_level") in setOf("LOW", "MEDIUM", "HIGH", "CRITICAL") &&
                it.getString("summary").isNotBlank()) { "Incomplete assessment." }
            for (key in listOf("coercion_markers", "evidence_extracted", "recommended_actions")) {
                val array = it.getJSONArray(key)
                check(array.length() <= 12)
                for (index in 0 until array.length()) check(array.get(index) is String)
            }
        }
    } finally { connection.disconnect() }
}

private fun reputationText(value: String): String = when (value) {
    "matched" -> "Safe Browsing flagged the URL. Avoid proceeding."
    "no_match" -> "No Safe Browsing match; this does not establish safety."
    "not_configured" -> "URL reputation is not configured. AI reviewed URL text only."
    "unavailable" -> "URL reputation lookup unavailable; no reputation verdict."
    else -> "No URL reputation lookup requested."
}

internal fun referenceNotes(result: JSONObject): String {
    val sources = result.optJSONArray("sources")
    if (sources == null || sources.length() == 0) return when (result.optString("grounding_status")) {
        "no_match" -> "No close reference found in the small advisory collection. This does not establish safety."
        "matched" -> "Related references were retrieved, but none was cited for this advice."
        "not_requested" -> "No usable text was available for reference retrieval."
        else -> "Reference retrieval unavailable. This answer is not backed by retrieved sources."
    }
    return buildString {
        append("Official guidance about reported patterns, not proof of the same scam or attacker.\n\n")
        for (i in 0 until sources.length()) {
            val source = sources.getJSONObject(i)
            append("${source.optString("publisher")} — ${source.optString("title")}\n")
            append("Published: ${source.optString("published")} · Reviewed: ${source.optString("reviewed")}\n")
            append("${source.optString("summary")}\n${source.optString("url")}\n\n")
        }
    }
}

private data class TriageTurn(val text: String, val context: String, val url: String, val image: Boolean, val assessment: JSONObject)

internal fun triageHeadline(level: String): String = when (level) {
    "HIGH", "CRITICAL" -> "Stop. This looks risky."
    "LOW" -> "No clear scam signs."
    else -> "Pause. Let’s check first."
}

internal fun connectionFailureMessage(origin: String, failure: Exception): String {
    val host = runCatching { URI(origin.trim()).host }.getOrNull()
    return when {
        failure is IllegalArgumentException -> "The app’s backend URL is invalid. Install the latest configured build."
        host == "10.0.2.2" -> "This address works only in an Android emulator. On a phone, install the build configured for the hosted backend."
        host in setOf("127.0.0.1", "localhost") -> "Cannot reach the local backend. Connect this phone by USB and set up adb reverse, or use your hosted HTTPS backend configured in this build."
        failure is java.net.SocketTimeoutException -> "The backend took too long to respond. Try again; local device checks still work."
        failure is javax.net.ssl.SSLException -> "Could not establish a secure connection. Check the backend URL and your phone’s date and time."
        else -> "Cannot reach the backend. Check your internet connection and the HTTPS URL configured in this build."
    }
}

@Composable
fun ScamTriagePanel(
    modifier: Modifier = Modifier,
    analyze: suspend (JSONObject) -> JSONObject = { submitTriage(BuildConfig.ASK_BACKEND_URL, "", it) },
    onSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    var message by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<ByteArray?>(null) }
    var mime by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var turns by remember { mutableStateOf<List<TriageTurn>>(emptyList()) }
    var details by remember { mutableStateOf<TriageTurn?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var consentOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(4 * 1024 * 1024) }
                            ?: error("Could not read image.")
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        require(options.outMimeType in setOf("image/jpeg", "image/png", "image/webp") &&
                            options.outWidth > 0 && options.outHeight > 0 &&
                            options.outWidth.toLong() * options.outHeight <= 20_000_000)
                        bytes to options.outMimeType
                    }
                    image = loaded.first; mime = loaded.second; error = null
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { image = null; mime = null; error = "Choose a JPEG, PNG or WebP under 4 MiB." }
                finally { busy = false }
            }
        }
    }
    fun send() {
        val text = message.trim()
        val link = Regex("https?://[^\\s]+").find(text)?.value ?: ""
        val prior = org.json.JSONArray()
        turns.forEach { prior.put(JSONObject().put("message", it.text).put("url", it.url)
            .put("note", if (it.image) "Earlier image is not included in this request." else "Text only.")) }
        val combined = if (turns.isEmpty()) text else JSONObject().put("earlier_user_messages", prior)
            .put("latest_user_message", text).toString()
        if (combined.length > 12000 || turns.size >= 6) { error = "This chat is full. Start a new chat."; return }
        val payload = JSONObject().put("consent", true)
        if (combined.isNotBlank()) payload.put("text", combined)
        if (link.isNotBlank()) payload.put("url", link)
        image?.let { payload.put("image_base64", Base64.encodeToString(it, Base64.NO_WRAP)).put("image_mime_type", mime) }
        val hadImage = image != null
        busy = true; error = null
        scope.launch {
            try {
                val answer = analyze(payload)
                turns = turns + TriageTurn(text, combined, link, hadImage, answer)
                message = ""; image = null; mime = null
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: AskSignInRequiredException) { error = "Please sign in with Google again to continue." }
            catch (failure: IllegalStateException) { error = failure.message ?: "Analysis unavailable." }
            catch (failure: Exception) { error = connectionFailureMessage(BuildConfig.ASK_BACKEND_URL, failure) }
            finally { busy = false }
        }
    }
    LaunchedEffect(turns.size, busy) {
        if (scroll.layoutInfo.totalItemsCount > 0) scroll.animateScrollToItem(scroll.layoutInfo.totalItemsCount - 1)
    }
    if (consentOpen) AlertDialog(onDismissRequest = { consentOpen = false },
        title = { Text("Send for an AI check?") },
        text = { Text("Your messages in this chat and the selected photo go to Dhalam’s backend and Google AI. Remove OTPs, passwords and account details first. Device checks stay on your phone. Earlier photos are not sent again. Links may be checked with Safe Browsing. Google service retention policies apply; Dhalam does not save submissions on its server.") },
        confirmButton = { Button(onClick = { consentOpen = false; send() }) { Text("Send for check") } },
        dismissButton = { TextButton(onClick = { consentOpen = false }) { Text("Not now") } })
    details?.let { turn ->
        val assessment = turn.assessment
        AlertDialog(onDismissRequest = { details = null }, title = { Text("Why this advice?") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(assessment.optString("summary"))
                for ((key, label) in listOf("coercion_markers" to "Warning signs", "evidence_extracted" to "What you shared", "recommended_actions" to "What to do")) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    val items = assessment.optJSONArray(key)
                    if (items != null) for (index in 0 until items.length()) Text("• ${items.optString(index)}")
                }
                Text(reputationText(assessment.optString("url_reputation")))
                Text(assessment.optString("limitations", "AI can be wrong. Verify independently."))
                Text("Sources for this advice", style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(referenceNotes(assessment)) }
                Text("AI concern: ${assessment.optInt("scam_probability")}/100. Not a measured probability.")
                Text("Model: ${assessment.optString("model")} · ${assessment.optString("analyzed_at")}")
            } },
            confirmButton = { TextButton(onClick = {
                details = null
                scope.launch {
                    val observations = withContext(Dispatchers.IO) {
                        runCatching { SecurityJournalStore.load(context).report() }.getOrDefault("Local history unavailable.")
                    }
                    preview = """DHALAM — PERSONAL CYBERCRIME INCIDENT NOTES
Prepared: ${Instant.now()}
User-reviewed draft, not an official complaint or forensic certification.

Incident time and timezone:
Sender/contact method:
Amount lost / transaction reference (never PINs or OTPs):
Actions already taken:

USER-SUBMITTED CONVERSATION CONTEXT
${turn.context}
URL: ${turn.url}
Original screenshot: attach separately if relevant.

AI ASSESSMENT — may use earlier messages in this chat; may be incorrect
Model: ${assessment.optString("model")}
Analyzed: ${assessment.optString("analyzed_at")}
Concern: ${assessment.optString("threat_level")} (${assessment.optInt("scam_probability")}/100, uncalibrated)
${assessment.optString("summary")}
Warning signs: ${assessment.optJSONArray("coercion_markers")}
Extracted evidence: ${assessment.optJSONArray("evidence_extracted")}
Next steps: ${assessment.optJSONArray("recommended_actions")}
${assessment.optString("limitations")}
${reputationText(assessment.optString("url_reputation"))}

REFERENCE GUIDANCE
${referenceNotes(assessment)}

LOCAL DEVICE OBSERVATIONS
$observations

Preserve original evidence and redact before sharing. Contact your bank and 1930 / https://cybercrime.gov.in for suspected financial fraud. No complaint has been filed.
"""
                }
            }) { Text("Preview report") } },
            dismissButton = { TextButton(onClick = { details = null }) { Text("Close") } })
    }
    if (preview != null) AlertDialog(onDismissRequest = { preview = null }, title = { Text("Review incident report") },
        text = { SelectionContainer { Text(preview!!, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = {
            try { (context as? Activity)?.let { shareSecurityReport(it, preview!!) } }
            catch (_: Exception) { error = "Could not open the share sheet." }
        }) { Text("Share report") } }, dismissButton = { TextButton(onClick = { preview = null }) { Text("Close") } })
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { turns = emptyList(); message = ""; image = null; mime = null; error = null }, enabled = !busy) { Text("New chat") }
            TextButton(onClick = onSignIn, enabled = !busy) { Text("Google account") }
        }
        androidx.compose.foundation.lazy.LazyColumn(state = scroll, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (turns.isEmpty()) item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp), color = colors.primaryContainer) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.fluent_shield), contentDescription = null, modifier = Modifier.padding(24.dp).size(64.dp), tint = colors.primary)
                    }
                    Text("Not sure?\nLet’s check together.", style = MaterialTheme.typography.headlineMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Tell me what happened, or show me a message.", style = MaterialTheme.typography.bodyLarge)
                    SuggestionChip(onClick = { message = "Someone is asking me to send money. " }, label = { Text("Someone wants money") })
                    Text("No need to decide in a hurry.", color = colors.onSurfaceVariant)
                }
            }
            turns.forEach { turn -> item {
                val assessment = turn.assessment
                val level = assessment.optString("threat_level")
                val urgent = level == "HIGH" || level == "CRITICAL"
                val low = level == "LOW"
                val accent = if (urgent) colors.error else if (low) colors.primary else androidx.compose.ui.graphics.Color(0xFF986000)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(modifier = Modifier.padding(start = 36.dp).fillMaxWidth(), color = colors.primaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
                        Text(turn.text.ifBlank { "Photo shared" } + if (turn.image && turn.text.isNotBlank()) "\nPhoto shared" else "", Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .3f))) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(if (urgent) "!" else if (low) "?" else "Ⅱ", color = accent, style = MaterialTheme.typography.displaySmall)
                            Text(triageHeadline(level), color = accent, style = MaterialTheme.typography.headlineSmall)
                            val summary = assessment.optString("summary").split(Regex("(?<=[.!?])\\s+")).first()
                            Text(if (summary.length > 200) summary.take(197) + "…" else summary, style = MaterialTheme.typography.bodyLarge)
                            assessment.optJSONArray("recommended_actions")?.optString(0)?.takeIf { it.isNotBlank() }?.let {
                                Text("→ $it", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(if (low) "This does not prove it is safe." else "AI can be wrong. Check independently.", color = colors.onSurfaceVariant)
                            TextButton(onClick = { details = turn }) { Text("Why? Show details") }
                        }
                    }
                }
            } }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("I’m checking. Take a breath.") }
            error?.let { item { Text(it, color = colors.error) } }
        }
        Surface(color = colors.surface, tonalElevation = 2.dp) {
            Column(Modifier.padding(12.dp)) {
                if (image != null) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Photo ready to check", Modifier.weight(1f))
                    TextButton(onClick = { image = null; mime = null }, enabled = !busy) { Text("Remove") }
                }
                OutlinedTextField(message, { if (it.length <= 12000) { message = it; error = null } }, enabled = !busy,
                    placeholder = { Text("Type or paste a message…") }, maxLines = 3,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch("image/*") }, enabled = !busy) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.material_attach), contentDescription = null, modifier = Modifier.size(22.dp))
                        Text("Photo")
                    }
                    Button(onClick = { consentOpen = true }, enabled = !busy && (message.isNotBlank() || image != null), modifier = Modifier.heightIn(min = 48.dp)) { Text("Check") }
                }
            }
        }
    }
}
