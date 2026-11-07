package com.dhalam.ap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun securityStamp(at: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(at))

data class SecurityFinding(val title: String, val detail: String, val state: String, val action: String) {
    fun json() = JSONObject().put("title", title).put("detail", detail).put("state", state).put("action", action)
}

fun explainSecurity(title: String, detail: String): SecurityFinding {
    val lower = detail.lowercase(Locale.ROOT)
    val (state, action) = when {
        "unavailable" in lower || "unknown" in lower -> "Unavailable" to "Review this setting manually; no passing result was recorded."
        title == "Device lock" -> if (lower.startsWith("not configured")) "Needs attention" to "Set a screen lock PIN, pattern or password in Settings."
            else "Configured" to "Use a strong private passcode. Its strength cannot be measured here."
        title == "Biometric authentication" -> if (lower.startsWith("ready")) "Configured" to "Review enrolled biometrics if you share this device."
            else "Needs attention" to "Review biometric settings. For a temporary lockout, unlock with your device passcode."
        title == "Notifications" -> (if (detail == "Permission granted") "Configured" else "Needs attention") to "Use Dhalam notification settings to manage permission."
        title == "Screen capture" -> (if (detail == "Active") "Needs attention" else "Configured") to
            (if (detail == "Active") "Dhalam is visible in a recording. Stop recording or sharing before viewing private reports if unintended." else "No active capture reported for Dhalam. This does not rule out every capture method.")
        title == "Operating system" -> "Manual review" to "Open Software Update in Settings. The version alone does not establish update status."
        else -> "Manual review" to "Review the reported setting and access in system Settings. Enabled access is not proof of abuse."
    }
    return SecurityFinding(title, detail, state, action)
}

data class SecurityCheckRecord(val at: Long, val findings: List<SecurityFinding>) {
    fun json() = JSONObject().put("at", at).put("findings", JSONArray(findings.map { it.json() }))
}

val securityReviewSteps = linkedMapOf(
    "App permissions" to "Settings → Privacy → Permission manager. Review location, camera, microphone and contacts; remove access you no longer need.",
    "Account security" to "Settings → Google → Manage your Google Account → Security. Review devices and 2-Step Verification.",
    "Software updates" to "Settings → System → Software update. Also review Google Play system updates in Security settings.",
    "Device management" to "Settings → Security → Device admin apps, and Network → VPN. Ask your administrator about work profiles before removing them.",
    "Sharing and access" to "Review Google Account → People & sharing → Location sharing, Maps location sharing, and Privacy → Permission manager. Android has no identical Apple Safety Check; review each separately. Changes to sharing may be noticed by others."
)

data class SecurityJournalData(val records: List<SecurityCheckRecord> = emptyList(), val reviewed: Map<String, Long> = emptyMap()) {
    fun json(): String = JSONObject().put("version", 1).put("records", JSONArray(records.map { it.json() }))
        .put("reviewed", JSONObject(reviewed)).toString()
    fun lastSuccess(title: String) = records.firstOrNull { r -> r.findings.any { it.title == title && it.state != "Unavailable" } }?.at
    fun changes(): List<String> {
        if (records.size < 2) return listOf("Run another check to compare observations.")
        val before = records[1].findings.associateBy { it.title }
        return records.first().findings.mapNotNull { current ->
            val old = before[current.title]
            when {
                old == null -> "${current.title}: first observation."
                old.state == "Unavailable" || current.state == "Unavailable" -> "${current.title}: comparison unavailable; one of the reads was unavailable."
                old.detail != current.detail -> "${current.title}: ${old.detail} → ${current.detail}"
                else -> null
            }
        }.ifEmpty { listOf("No changes in the comparable checks.") }
    }
    fun report(): String = buildString {
        appendLine("Dhalam security report\nGenerated: ${securityStamp(System.currentTimeMillis())}\nPlatform: Android")
        appendLine("Configuration observations, not a malware scan or safety verdict. Latest 30 successful foreground checks. Inventories are summaries, not per-app history.")
        appendLine("\nCHANGES SINCE PREVIOUS CHECK")
        changes().forEach { appendLine(it) }
        appendLine("\nOBSERVATIONS (newest first)")
        if (records.isEmpty()) appendLine("No check recorded.")
        records.forEach { r ->
            appendLine("\nChecked: ${securityStamp(r.at)}")
            r.findings.forEach { appendLine("${it.title} [${it.state}]: ${it.detail}\nNext step: ${it.action}") }
        }
        appendLine("\nMANUAL REVIEW — user-confirmed, not automatically verified")
        securityReviewSteps.keys.forEach { appendLine("$it: ${reviewed[it]?.let { at -> "User-confirmed ${securityStamp(at)}" } ?: "Not confirmed"}") }
        appendLine("\nUnavailable readings are not passing results. Capture observations cover supported detection while Dhalam is visible. Pasted links are never included in this report.")
    }
}

/** Serializes read-modify-write transactions, including foreground checks and checklist edits. */
object SecurityJournalStore {
    @Synchronized fun load(context: Context): SecurityJournalData {
        val raw = ToolsStorage(context).load() ?: return SecurityJournalData()
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "Unsupported journal version" }
        val records = json.getJSONArray("records")
        val reviewed = json.getJSONObject("reviewed")
        return SecurityJournalData((0 until minOf(records.length(), 30)).map { i ->
            val r = records.getJSONObject(i)
            val f = r.getJSONArray("findings")
            SecurityCheckRecord(r.getLong("at"), (0 until f.length()).map { j ->
                val item = f.getJSONObject(j)
                SecurityFinding(item.getString("title"), item.getString("detail"), item.getString("state"), item.getString("action"))
            })
        }, reviewed.keys().asSequence().associateWith { reviewed.getLong(it) })
    }
    @Synchronized fun record(context: Context, observation: SecurityObservation, notifications: Boolean, capture: String) {
        val old = load(context)
        if (old.records.any { it.at == observation.observedAt }) return
        val findings = (observation.deviceSignals + observation.developerSignals).map { explainSecurity(it.label, it.status) } + listOf(
            explainSecurity("Notifications", if (notifications) "Permission granted" else "Permission not granted"),
            explainSecurity("Screen capture", capture),
            explainSecurity("Accessibility inventory", observation.services?.let { "${it.size} enabled services reported" } ?: "Unavailable"),
            explainSecurity("Permission inventory", observation.permissionInventory?.let { "${it.packageCount} visible packages; ${it.permissions.sumOf { p -> p.grantedCount }} granted app-permission pairs" } ?: "Unavailable")
        )
        ToolsStorage(context).save(old.copy(records = (listOf(SecurityCheckRecord(observation.observedAt, findings)) + old.records).take(30)).json())
    }
    @Synchronized fun review(context: Context, id: String, checked: Boolean): SecurityJournalData {
        val old = load(context)
        val reviews = old.reviewed.toMutableMap()
        if (checked) reviews[id] = System.currentTimeMillis() else reviews.remove(id)
        return old.copy(reviewed = reviews).also { ToolsStorage(context).save(it.json()) }
    }
    @Synchronized fun clear(context: Context): SecurityJournalData = SecurityJournalData().also { ToolsStorage(context).save(it.json()) }
}
