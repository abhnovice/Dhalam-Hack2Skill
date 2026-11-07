package com.dhalam.dhalam

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
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
    val permissionInventory: PermissionInventory?,
    val deviceSignals: List<DeveloperSignal> = emptyList()
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
        permissionInventory = inventory,
        deviceSignals = collectDeviceSignals(context)
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
/** Configuration checks only: these do not authenticate or assess passcode strength. */
private fun collectDeviceSignals(context: Context): List<DeveloperSignal> {
    val lockStatus = try {
        when (context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure) {
            true -> "Configured — PIN, pattern or password is set"
            false -> "Not configured — set a screen lock in Settings"
            null -> "Unavailable — could not determine screen lock status"
        }
    } catch (_: SecurityException) {
        "Unavailable — access restricted"
    }
    val biometricStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val manager = context.getSystemService(BiometricManager::class.java)
            @Suppress("DEPRECATION")
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            } else {
                manager?.canAuthenticate()
            }
            when (status) {
                BiometricManager.BIOMETRIC_SUCCESS -> "Ready — biometrics are enrolled and available to this app"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Not enrolled — configure biometrics in Settings"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Unavailable — no supported biometric hardware"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Temporarily unavailable — try again later"
                else -> "Unavailable — hardware or device policy may restrict access"
            }
        } catch (_: SecurityException) {
            "Unavailable — access restricted"
        }
    } else {
        "Unavailable — this check requires Android 10 or later"
    }
    return listOf(
        DeveloperSignal("Device lock", lockStatus),
        DeveloperSignal("Biometric authentication", biometricStatus),
        DeveloperSignal("Operating system", "Android ${Build.VERSION.RELEASE} — review Software Update in Settings")
    )
}
