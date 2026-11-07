package com.dhalam.ap

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class PermissionExport(val title: String, val fileName: String) {
    Apps("Apps and Permissions report", "apps-and-permissions.csv"),
    TimestampLog("Apps-Permission-Timestamp log", "apps-permission-timestamp.csv")
}

/** Exports observed permission state; timestamps are scan times, not permission-use times. */
fun SecurityObservation.permissionCsv(type: PermissionExport): String {
    val inventory = requireNotNull(permissionInventory)
    fun row(vararg cells: String) = cells.joinToString(",") {
        // Prevent spreadsheet formulas in app-controlled labels and permission names.
        val safe = if (it.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$it" else it
        "\"" + safe.replace("\"", "\"\"") + "\""
    } + "\r\n"
    fun status(granted: Boolean?) = when (granted) {
        true -> "Granted"
        false -> "Not granted"
        null -> "Unknown"
    }
    return buildString {
        if (type == PermissionExport.Apps) {
            append(row("App", "Package", "Permissions (observed status)"))
            inventory.permissions.flatMap { report -> report.apps.map { report.name to it } }
                .groupBy { it.second.packageName }.toSortedMap().forEach { (pkg, entries) ->
                    append(row(entries.first().second.label, pkg,
                        entries.sortedBy { it.first }.joinToString("; ") {
                            "${it.first}: ${status(it.second.granted)}"
                        }))
                }
        } else {
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(observedAt))
            append(row("App", "Package", "Permission", "Status", "Observed at (UTC)"))
            inventory.permissions.sortedBy { it.name }.forEach { report ->
                report.apps.sortedBy { it.packageName }.forEach { app ->
                    append(row(app.label, app.packageName, report.name, status(app.granted), timestamp))
                }
            }
        }
    }
}
