package com.dhalam.ap

import com.dhalam.ap.data.SecurityFact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

// Optional memory-only Google credential. Local sessions send no identity token.
class AskBackendSession(val url: String, val idToken: String? = null)
class AskSignInRequiredException : Exception("Please sign in with Google again.")


data class AskFinding(val title: String, val severity: String, val explanation: String, val sources: List<SecurityFact>)
data class AskResult(
    val answer: String,
    val findings: List<AskFinding>,
    val recommendations: List<String>,
    val limitations: List<String>,
    val observedAt: Long,
    val evidenceCount: Int
)

class AskBackend {
    suspend fun verifySession(session: AskBackendSession) = withContext(Dispatchers.IO) {
        val connection = URL("${session.url}/check").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            session.idToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            checkResponseCode(connection.responseCode)
        } finally { connection.disconnect() }
    }

    suspend fun ask(settings: AskBackendSession, question: String,
                    facts: List<SecurityFact>, snapshot: SecurityObservation): AskResult = withContext(Dispatchers.IO) {
        val selected = selectAskEvidence(facts, question, snapshot.observedAt).toMutableList()
        check(selected.isNotEmpty()) { "No current saved scan facts. Tap Scan now and try again." }
        fun payload(): JSONObject {
            val evidence = JSONArray()
            selected.forEach { fact ->
                evidence.put(JSONObject().put("id", "fact:${fact.id}")
                    .put("kind", fact.kind.take(64)).put("subjectId", fact.subjectId.take(256))
                    .put("detail", fact.detail.take(1000)).put("status", fact.status.take(100))
                    .put("observedAt", fact.observedAt))
            }
            val currentCount = facts.count { it.observedAt == snapshot.observedAt }
            val coverage = "Selected ${selected.size} of $currentCount facts at scan ${snapshot.observedAt}. " +
                "Visible apps: ${snapshot.permissionInventory?.packageCount ?: "unavailable"}. " +
                "Permission inventory: ${if (snapshot.permissionInventory == null) "unavailable" else "available"}. " +
                "Enabled accessibility list: ${if (snapshot.services == null) "unavailable" else "available"}. " +
                "Developer signals may require checking Settings. Retained older facts are excluded. " +
                "This is a selected subset, not a complete device risk assessment. " +
                "Details may be shortened. No actual permission-use events were collected."
            return JSONObject().put("question", question).put("coverage", coverage).put("evidence", evidence)
        }
        var body = payload().toString().toByteArray(Charsets.UTF_8)
        while (body.size > 90 * 1024 && selected.size > 1) {
            selected.removeAt(selected.lastIndex)
            body = payload().toString().toByteArray(Charsets.UTF_8)
        }
        check(body.size <= 90 * 1024) { "The evidence is too large to send." }
        val connection = URL("${settings.url}/ask").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            settings.idToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(body.size)
            coroutineContext.ensureActive()
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            checkResponseCode(code)
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 256 * 1024) { "The backend returned an oversized answer." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            coroutineContext.ensureActive()
            parseAskResult(bytes.toString(Charsets.UTF_8), selected, snapshot.observedAt)
        } finally {
            connection.disconnect()
        }
    }
}

private fun checkResponseCode(code: Int) {
    if (code == 401) throw AskSignInRequiredException()
    if (code != 200) throw IllegalStateException(when (code) {
        403 -> "This Google account is not authorized. Use an allowed account with a verified email."
        413, 422 -> "The scan could not be processed. Scan again and retry."
        429 -> "The service limit was reached. Please try again later."
        503 -> "The service is not ready. Please try again later."
        504 -> "The answer timed out. Please try again."
        else -> "An answer is unavailable right now. Please try again."
    })
}

internal fun parseAskResult(text: String, facts: List<SecurityFact>, observedAt: Long): AskResult {
    val json = JSONObject(text)
    check(json.has("exposureScore") && json.isNull("exposureScore")) { "Scoring is not available in this stage." }
    check(json.has("scoreExplanation") && json.isNull("scoreExplanation")) { "Unexpected score explanation." }
    fun strings(array: JSONArray, max: Int = 12, optionalText: Boolean = false): List<String> {
        check(array.length() <= max)
        return (0 until array.length()).mapNotNull {
            val value = array.get(it) as? String ?: error("Invalid answer text.")
            check(value.length <= 12000) { "Invalid answer text." }
            if (optionalText && value.isBlank()) return@mapNotNull null
            check(value.isNotBlank()) { "Invalid answer text." }
            value.trim()
        }
    }
    val sources = facts.associateBy { "fact:${it.id}" }
    val findings = json.getJSONArray("findings")
    check(findings.length() <= 12)
    val parsedFindings = (0 until findings.length()).map { index ->
        val finding = findings.getJSONObject(index)
        val ids = strings(finding.getJSONArray("evidenceIds"), 64)
        check(ids.isNotEmpty() && ids.all { it in sources }) { "The answer cited unsupported evidence." }
        val severity = finding.getString("severity")
        check(severity in listOf("low", "medium", "high"))
        AskFinding(finding.getString("title"), severity, finding.getString("explanation"),
            ids.distinct().map { sources.getValue(it) })
    }
    val answer = json.getString("answer")
    check(answer.isNotBlank() && answer.length <= 12000)
    return AskResult(answer, parsedFindings, strings(json.getJSONArray("recommendations"), optionalText = true),
        strings(json.getJSONArray("limitations"), optionalText = true), observedAt, facts.size)
}
