package com.dhalam.ap

import com.dhalam.ap.data.SecurityFact
import java.util.Locale

/** Deterministic text retrieval for this stage. No embedding or risk scoring is implied. */
fun selectAskEvidence(facts: List<SecurityFact>, question: String, observedAt: Long): List<SecurityFact> {
    val query = question.lowercase(Locale.ROOT)
    val stopWords = setOf("the", "and", "for", "are", "this", "that", "what", "which", "does", "could", "with", "have", "app", "apps", "permission", "permissions")
    val words = Regex("[a-z0-9._]+").findAll(query).map { it.value }
        .filter { it.length > 2 && it !in stopWords }.toSet()
    val sensitive = listOf("CAMERA", "RECORD_AUDIO", "READ_CONTACTS", "READ_SMS", "RECEIVE_SMS",
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_PHONE_STATE", "CALL_PHONE",
        "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES")
    return facts.asSequence().filter { it.observedAt == observedAt }
        .sortedWith(compareByDescending<SecurityFact> { fact ->
            val text = "${fact.subjectId} ${fact.detail} ${fact.status}".lowercase(Locale.ROOT)
            val packageMatch = if (fact.subjectId.contains('.') &&
                query.contains(fact.subjectId.lowercase(Locale.ROOT))) 1000 else 0
            packageMatch + words.count { text.contains(it) } * 100 +
                when {
                    fact.kind == "accessibility_enabled" -> 50
                    fact.kind == "developer" -> 20
                    fact.status == "granted" && sensitive.any { fact.detail.contains(it) } -> 30
                    else -> 0
                }
        }.thenBy { it.stableKey }).take(48).toList()
}

fun normalizeBackendUrl(raw: String): String {
    val uri = try { java.net.URI(raw.trim()) } catch (_: Exception) {
        throw IllegalArgumentException("Enter a valid backend URL.")
    }
    require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null &&
        uri.rawFragment == null && (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
        (uri.port == -1 || uri.port in 1..65535)) { "Use a backend origin URL without credentials, a path, or a query." }
    require(uri.scheme == "https" || (uri.scheme == "http" &&
        uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost"))) {
        "Use HTTPS, or HTTP only for 10.0.2.2, localhost, or 127.0.0.1."
    }
    return raw.trim().trimEnd('/')
}
