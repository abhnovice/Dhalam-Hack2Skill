package com.dhalam.ap

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothReviewRegressionTest {
    @Test fun systemUiCleanupPreservesCodeUntilExplicitCancel() {
        val model = BluetoothReviewViewModel(ApplicationProvider.getApplicationContext<Application>())
        model.code("123456")
        model.stopTransfers()
        assertEquals("123456", model.state.enteredCode)
        model.stopTransfers(clearCode = true)
        assertEquals("", model.state.enteredCode)
    }

    @Test fun partialPermissionCoverageSurvivesTransferAndLegacyPayloads() {
        val inventory = RemoteDeviceInventory("TV", "14", 1, 1, listOf(
            RemoteApp("example.tv", "TV app", listOf(RemotePermission("android.permission.CAMERA", "unknown")), 500)))
        val decoded = RemoteDeviceInventory.parse(inventory.json())
        assertEquals(500, decoded.apps.single().totalRequestedPermissions)
        assertEquals("unknown", decoded.apps.single().permissions.single().status)
        val legacy = inventory.json()
        legacy.getJSONArray("apps").getJSONObject(0).remove("totalRequestedPermissions")
        assertEquals(1, RemoteDeviceInventory.parse(legacy).apps.single().totalRequestedPermissions)
    }

    @Test fun oversizedInventoriesFitFrameWithoutLosingCoverageTotals() {
        val apps = (0 until 20).map { RemoteApp("example.app$it", "A".repeat(160),
            listOf(RemotePermission("android.permission.CAMERA", "granted"))) }
        val inventory = RemoteDeviceInventory("TV", "14", 1, 20, apps)
        val bounded = boundedRemoteInventory(inventory, 800)
        assertTrue(bounded.json().toString().toByteArray().size <= 800)
        assertTrue(bounded.apps.size < 20)
        assertEquals(20, bounded.totalVisibleApps)
        assertEquals(bounded, RemoteDeviceInventory.parse(bounded.json()))
    }

    @Test fun realInventoryDoesNotFailOnLargeSystemPermissionLists() {
        val inventory = collectRemoteDeviceInventory(ApplicationProvider.getApplicationContext())
        assertTrue(inventory.apps.all { it.permissions.size <= 256 && it.totalRequestedPermissions >= it.permissions.size })
        assertTrue(inventory.json().toString().toByteArray().size <= DeviceReviewProtocol.MAX_BYTES - 1024)
        assertEquals(inventory, RemoteDeviceInventory.parse(inventory.json()))
    }
}
