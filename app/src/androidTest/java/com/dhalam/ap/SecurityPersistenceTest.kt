package com.dhalam.ap

import androidx.test.platform.app.InstrumentationRegistry
import com.dhalam.ap.data.*
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class SecurityPersistenceTest {
    private lateinit var directory: File
    private lateinit var store: BoxStore
    private lateinit var repository: SecurityRepository

    @Before fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "persistence-test-${UUID.randomUUID()}")
        reopen()
    }

    private fun reopen() {
        store = MyObjectBox.builder().directory(directory).build()
        repository = SecurityRepository(store)
    }

    @After fun close() {
        store.close()
        directory.deleteRecursively()
    }

    private fun snapshot(time: Long, granted: Boolean? = true) = SecurityObservation(
        time, emptyList(), emptyList(),
        listOf(DeveloperSignal("USB debugging", "Check in Settings")),
        PermissionInventory(1, listOf(PermissionReport("android.permission.CAMERA",
            listOf(PermissionApp("example.app", "Example", granted)))))
    )

    @Test fun persistsAcrossReopenAndKeepsIdsOnUpdate() {
        repository.saveObservation(snapshot(10))
        val original = repository.all().single { it.kind == "permission" }
        store.close()
        reopen()
        assertEquals("granted", repository.find(original.stableKey)?.status)
        repository.saveObservation(snapshot(20, false))
        val updated = repository.find(original.stableKey)!!
        assertEquals(original.id, updated.id)
        assertEquals("not_granted", updated.status)
        assertEquals(2L, repository.count())
        repository.saveObservation(snapshot(15))
        assertEquals("not_granted", repository.find(original.stableKey)?.status)
    }

    @Test fun unavailableRetainsFactsButSuccessfulEmptyRemovesThem() {
        repository.saveObservation(snapshot(10))
        repository.saveObservation(snapshot(20).copy(permissionInventory = null))
        assertEquals(1, repository.all().count { it.kind == "permission" })
        repository.saveObservation(snapshot(30).copy(permissionInventory = PermissionInventory(0, emptyList())))
        assertEquals(0, repository.all().count { it.kind == "permission" })
        // The empty scan watermark must also reject a late result.
        repository.saveObservation(snapshot(25))
        assertEquals(0, repository.all().count { it.kind == "permission" })
    }

    @Test fun storesUnknownAndDoesNotInferDeveloperSettings() {
        repository.saveObservation(snapshot(10, null))
        assertEquals("unknown", repository.all().single { it.kind == "permission" }.status)
        assertEquals("Check in Settings", repository.all().single { it.kind == "developer" }.status)
    }

    @Test fun removedAccessibilityServicesAreDeletedOnlyWhenAvailable() {
        val enabled = snapshot(10).copy(services = listOf(
            ObservedAccessibilityService("Service", "example.app/.Service", listOf("Read window content"))
        ))
        repository.saveObservation(enabled)
        repository.saveObservation(snapshot(20).copy(services = null))
        assertEquals(1, repository.all().count { it.kind == "accessibility_enabled" })
        repository.saveObservation(snapshot(30))
        assertEquals(0, repository.all().count { it.kind == "accessibility_enabled" })
    }
}
