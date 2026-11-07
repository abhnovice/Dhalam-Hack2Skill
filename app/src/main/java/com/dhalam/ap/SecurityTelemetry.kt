package com.dhalam.ap

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

data class ObservedAccessibilityService(
    val name: String,
    val id: String?,
    val capabilities: List<String>
)

data class DeveloperSignal(val label: String, val status: String)

data class PermissionApp(
    val packageName: String,
    val label: String,
    val granted: Boolean?
)

data class PermissionReport(
    val name: String,
    val apps: List<PermissionApp>
) {
    val grantedCount: Int
        get() = apps.count { it.granted == true }
}

data class PermissionInventory(
    val packageCount: Int,
    val permissions: List<PermissionReport>
)

data class SecurityObservation(
    val observedAt: Long,
    val services: List<ObservedAccessibilityService>?,
    val installedServices: List<ObservedAccessibilityService>?,
    val developerSignals: List<DeveloperSignal>,
    val permissionInventory: PermissionInventory?
)

fun normalizedServiceId(id: String?): String? =
    id?.let { ComponentName.unflattenFromString(it)?.flattenToShortString() }

fun collectSecurityObservation(context: Context): SecurityObservation {
    val manager = context.getSystemService(AccessibilityManager::class.java)
    val packageManager = context.packageManager

    val enabled = try {
        manager?.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )?.map { describeService(it, packageManager) }
    } catch (_: SecurityException) {
        null
    }

    val installed = try {
        manager?.installedAccessibilityServiceList
            ?.map { describeService(it, packageManager) }
    } catch (_: SecurityException) {
        null
    }

    val developerSignals = listOf(
        DeveloperSignal("Developer options", "Check in Settings"),
        DeveloperSignal("USB debugging", "Check in Settings"),
        DeveloperSignal("Wireless debugging", "Check in Settings"),
        DeveloperSignal(
            "Stay awake while charging",
            readGlobalStatus(context, Settings.Global.STAY_ON_WHILE_PLUGGED_IN)
        ),
        DeveloperSignal(
            "Don't keep activities",
            readGlobalStatus(context, Settings.Global.ALWAYS_FINISH_ACTIVITIES)
        )
    )

    val inventory = collectPermissionInventory(packageManager)

    return SecurityObservation(
        observedAt = System.currentTimeMillis(),
        services = enabled,
        installedServices = installed,
        developerSignals = developerSignals,
        permissionInventory = inventory
    )
}

private fun readGlobalStatus(context: Context, key: String): String = try {
    when (Settings.Global.getString(context.contentResolver, key)?.toIntOrNull()) {
        null -> "Unavailable"
        0 -> "Reported off"
        else -> "Reported on"
    }
} catch (_: SecurityException) {
    "Unavailable"
}

private fun describeService(
    service: AccessibilityServiceInfo,
    packageManager: PackageManager
): ObservedAccessibilityService {
    val checks = listOf(
        AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT to
                "Read window content",
        AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES to
                "Perform taps and gestures",
        AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS to
                "Request key-event filtering",
        AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT to
                "Take screenshots",
        AccessibilityServiceInfo.CAPABILITY_CAN_CONTROL_MAGNIFICATION to
                "Control magnification",
        AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION to
                "Request touch exploration",
        AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES to
                "Request fingerprint gestures"
    )

    return ObservedAccessibilityService(
        name = service.resolveInfo.loadLabel(packageManager).toString(),
        id = service.id,
        capabilities = checks.filter { (flag, _) ->
            (service.capabilities and flag) != 0
        }.map { (_, label) -> label }
    )
}

@Suppress("DEPRECATION")
private fun collectPermissionInventory(
    packageManager: PackageManager
): PermissionInventory? {
    val packages = try {
        packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    } catch (_: SecurityException) {
        return null
    }

    val byPermission = mutableMapOf<String, MutableList<PermissionApp>>()

    for (pkg in packages) {
        val permissions = pkg.requestedPermissions ?: continue
        val flags = pkg.requestedPermissionsFlags
        val label = pkg.applicationInfo?.loadLabel(packageManager)?.toString()
            ?: pkg.packageName

        permissions.forEachIndexed { index, permission ->
            val granted = flags?.getOrNull(index)?.let { flag ->
                (flag and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }

            byPermission.getOrPut(permission) { mutableListOf() }
                .add(PermissionApp(pkg.packageName, label, granted))
        }
    }

    val reports = byPermission.map { (name, apps) ->
        PermissionReport(
            name,
            apps.sortedWith(
                compareByDescending<PermissionApp> { it.granted == true }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.packageName }
            )
        )
    }.sortedWith(
        compareByDescending<PermissionReport> { it.grantedCount }
            .thenBy { it.name }
    )

    return PermissionInventory(packages.size, reports)
}