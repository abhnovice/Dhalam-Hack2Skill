package com.dhalam.ap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Only the reviewed accessibility observations are sent; the app inventory stays on device. */
internal suspend fun requestAccessibilityReport(session: AskBackendSession, snapshot: SecurityObservation): String =
    withContext(Dispatchers.IO) {
        val settings = JSONArray()
        snapshot.accessibilitySettings.take(48).forEach {
            settings.put(JSONObject().put("name", it.label.take(160)).put("status", it.status.take(200)))
        }
        val services = JSONArray()
        snapshot.services.orEmpty().take(64).forEach {
            services.put(JSONObject().put("name", it.name.take(256))
                .put("capabilities", JSONArray(it.capabilities.take(16))))
        }
        val payload = JSONObject().put("consent", true).put("observedAt", snapshot.observedAt)
            .put("settings", settings).put("enabledServices", services)
            .put("serviceListAvailable", snapshot.services != null)
        val connection = URL("${session.url}/accessibility-report").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            session.idToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            coroutineContext.ensureActive()
            connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode == 401) throw AskSignInRequiredException()
            check(connection.responseCode == 200) {
                when (connection.responseCode) {
                    403 -> "This account is not allowed to request a report."
                    429 -> "Too many requests. Please try again later."
                    502 -> "Gemini could not prepare the report. Local settings are still available."
                    503 -> "The AI service is not configured yet. Local settings are still available."
                    else -> "The AI report is unavailable. Your local settings are still available."
                }
            }
            val body = connection.inputStream.use { it.readBounded(16_384) }.toString(Charsets.UTF_8)
            coroutineContext.ensureActive()
            JSONObject(body).getString("report").also {
                check(it.trim().split(Regex("\\s+")).size in 50..100) { "The AI report was incomplete. Please retry." }
            }
        } finally { connection.disconnect() }
    }
