package com.dhalam.ap

import com.dhalam.ap.data.SecurityFact
import java.util.Locale

/** Answer narrow permission inventory questions from the scan, without a network request. */
internal fun localAskAnswer(
    question: String,
    facts: List<SecurityFact>,
    snapshot: SecurityObservation
): AskResult? {
    val query = question.lowercase(Locale.ROOT)
    val asksWhichApp = Regex("\\b(which|what|list|show|name)\\b").containsMatchIn(query)
    val asksCamera = Regex("\\b(camera|video|recording videos?)\\b").containsMatchIn(query)
    if (!asksWhichApp || !asksCamera || snapshot.permissionInventory == null) return null

    val cameraApps = snapshot.permissionInventory.permissions
        .firstOrNull { it.name == "android.permission.CAMERA" }
        ?.apps?.filter { it.granted == true }
        ?.distinctBy { it.packageName }
        ?.sortedBy { it.packageName } ?: return null
    val cameraFacts = facts.filter { fact ->
        fact.observedAt == snapshot.observedAt && fact.kind == "permission" &&
            fact.status == "granted" &&
            fact.detail.contains("android.permission.CAMERA", ignoreCase = true)
    }.filter { fact -> cameraApps.any { it.packageName == fact.subjectId } }
        .distinctBy { it.subjectId }.sortedBy { it.subjectId }
    val shown = cameraApps.take(50).map { "${it.label} (${it.packageName})" }
    val answer = if (cameraApps.isEmpty()) {
        "The latest scan found no visible apps with camera permission granted."
    } else {
        "${cameraApps.size} visible ${if (cameraApps.size == 1) "app has" else "apps have"} camera permission: " +
            shown.joinToString(", ") + if (cameraApps.size > shown.size) ", and ${cameraApps.size - shown.size} more." else "."
    }
    return AskResult(
        answer = "$answer Camera permission means an app may access the camera; this scan cannot tell whether it recorded video or did so without your knowledge.",
        findings = cameraFacts.take(6).map { fact ->
            AskFinding(fact.subjectId, "low", "Camera permission was granted at the latest scan.", listOf(fact))
        },
        recommendations = listOf("Review camera access in Android Settings and revoke it for apps you do not trust."),
        limitations = listOf("Only apps visible to this scan are included. Granted permission is not evidence of camera use."),
        observedAt = snapshot.observedAt,
        evidenceCount = cameraFacts.size
    )
}
