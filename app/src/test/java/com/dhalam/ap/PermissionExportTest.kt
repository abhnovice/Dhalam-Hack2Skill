package com.dhalam.ap

import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionExportTest {
    private val snapshot = SecurityObservation(
        observedAt = 0L,
        services = null,
        installedServices = null,
        developerSignals = emptyList(),
        permissionInventory = PermissionInventory(1, listOf(
            PermissionReport("android.permission.CAMERA", listOf(
                PermissionApp("example.app", "App, \"quoted\"", true)
            )),
            PermissionReport("android.permission.RECORD_AUDIO", listOf(
                PermissionApp("example.app", "App, \"quoted\"", null)
            ))
        ))
    )

    @Test fun reportGroupsPermissionsAndEscapesLabels() {
        val csv = snapshot.permissionCsv(PermissionExport.Apps)
        assertTrue(csv.contains("\"App, \"\"quoted\"\"\""))
        assertTrue(csv.contains("android.permission.CAMERA: Granted; android.permission.RECORD_AUDIO: Unknown"))
        assertTrue(csv.split("\r\n").filter { it.isNotEmpty() }.size == 2)
    }

    @Test fun timestampLogUsesObservationTimeInUtc() {
        val csv = snapshot.permissionCsv(PermissionExport.TimestampLog)
        assertTrue(csv.contains("\"android.permission.CAMERA\",\"Granted\",\"1970-01-01T00:00:00.000Z\""))
        assertTrue(csv.contains("\"android.permission.RECORD_AUDIO\",\"Unknown\""))
    }

    @Test fun appLabelsCannotBecomeSpreadsheetFormulas() {
        val unsafe = snapshot.copy(permissionInventory = PermissionInventory(1, listOf(
            PermissionReport("CAMERA", listOf(PermissionApp("example.app", "=1+1", false)))
        )))
        val csv = unsafe.permissionCsv(PermissionExport.TimestampLog)
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"Not granted\""))
    }
}
