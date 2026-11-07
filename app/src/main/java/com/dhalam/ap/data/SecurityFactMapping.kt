package com.dhalam.ap.data

import com.dhalam.ap.SecurityObservation

data class SecurityFactBatch(val kind: String, val facts: List<SecurityFact>)

/** Null scan categories produce no batch; successful empty categories produce an empty batch. */
fun SecurityObservation.factBatches(): List<SecurityFactBatch> = buildList {
    fun fact(kind: String, subject: String, key: String, detail: String, status: String) =
        SecurityFact(
            stableKey = "$kind:${subject.length}:$subject:$key",
            kind = kind, subjectId = subject, detail = detail,
            status = status, observedAt = observedAt
        )

    permissionInventory?.let { inventory ->
        add(SecurityFactBatch("permission", inventory.permissions.flatMap { report ->
            report.apps.map { app ->
                fact("permission", app.packageName, report.name,
                    "${app.label} (${app.packageName}) requested ${report.name}",
                    when (app.granted) {
                        true -> "granted"
                        false -> "not_granted"
                        null -> "unknown"
                    })
            }
        }))
    }
    // A missing service ID cannot safely identify the whole category for replacement.
    listOf("accessibility_enabled" to services, "accessibility_installed" to installedServices)
        .forEach { (kind, entries) ->
            if (entries != null && entries.all { !it.id.isNullOrBlank() }) {
                add(SecurityFactBatch(kind, entries.map { service ->
                    fact(kind, requireNotNull(service.id), "",
                        "${service.name}; capabilities: ${service.capabilities.joinToString(", ")}",
                        if (kind == "accessibility_enabled") "enabled" else "installed")
                }))
            }
        }
    add(SecurityFactBatch("device", deviceSignals.map { signal ->
        fact("device", signal.label, "", signal.label, signal.status)
    }))
    add(SecurityFactBatch("developer", developerSignals.map { signal ->
        fact("developer", signal.label, "", signal.label, signal.status)
    }))
}
