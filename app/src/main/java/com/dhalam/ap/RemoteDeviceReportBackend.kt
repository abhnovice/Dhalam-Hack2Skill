package com.dhalam.ap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal val remoteReportPermissions = setOf(
    "android.permission.CAMERA", "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS",
    "android.permission.READ_SMS", "android.permission.SEND_SMS",
    "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
    "android.permission.READ_PHONE_STATE", "android.permission.READ_MEDIA_IMAGES"
)

internal fun remoteReportPayload(inventory: RemoteDeviceInventory): JSONObject {
    val apps = JSONArray(inventory.apps.take(100).map { app ->
        JSONObject().put("packageName", app.packageName).put("label", app.label)
            .put("permissions", JSONArray(app.permissions.filter { it.name in remoteReportPermissions }.map {
                JSONObject().put("name", it.name).put("status", it.status)
            }))
    })
    return JSONObject().put("consent", true).put("deviceName", inventory.deviceName)
        .put("androidVersion", inventory.androidVersion).put("observedAt", inventory.observedAt)
        .put("totalVisibleApps", inventory.totalVisibleApps).put("apps", apps)
}

internal suspend fun requestRemoteDeviceReport(session: AskBackendSession, inventory: RemoteDeviceInventory): String =
    withContext(Dispatchers.IO) {
        val connection = URL("${session.url}/device-report").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            session.idToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            val body = remoteReportPayload(inventory).toString().toByteArray(Charsets.UTF_8)
            require(body.size <= 90 * 1024) { "Too much data for an AI brief. The local review is still available." }
            connection.setFixedLengthStreamingMode(body.size)
            coroutineContext.ensureActive()
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode == 401) throw AskSignInRequiredException()
            check(connection.responseCode == 200) {
                when (connection.responseCode) {
                    403 -> "This account cannot request a brief."
                    429 -> "Please wait before requesting another brief."
                    503 -> "Gemini is not configured yet. Local review still works."
                    else -> "Gemini could not prepare the brief. Local review still works."
                }
            }
            val response = connection.inputStream.use { it.readBounded(16384) }.toString(Charsets.UTF_8)
            coroutineContext.ensureActive()
            JSONObject(response).getString("brief").also {
                require(it.length <= 1200 && it.trim().split(Regex("\\s+")).size in 15..80) { "Incomplete AI brief. Try again." }
            }
        } finally { connection.disconnect() }
    }
