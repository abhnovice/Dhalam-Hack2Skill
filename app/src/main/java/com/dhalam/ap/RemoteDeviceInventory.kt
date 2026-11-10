package com.dhalam.ap

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class RemotePermission(val name: String, val status: String)
data class RemoteApp(val packageName: String, val label: String, val permissions: List<RemotePermission>)
data class RemoteDeviceInventory(
    val deviceName: String,
    val androidVersion: String,
    val observedAt: Long,
    val totalVisibleApps: Int,
    val apps: List<RemoteApp>
) {
    fun json(): JSONObject = JSONObject().put("deviceName", deviceName).put("androidVersion", androidVersion)
        .put("observedAt", observedAt).put("totalVisibleApps", totalVisibleApps)
        .put("apps", JSONArray(apps.map { app ->
            JSONObject().put("packageName", app.packageName).put("label", app.label)
                .put("permissions", JSONArray(app.permissions.map {
                    JSONObject().put("name", it.name).put("status", it.status)
                }))
        }))

    companion object {
        fun parse(json: JSONObject): RemoteDeviceInventory {
            fun text(obj: JSONObject, key: String, limit: Int): String {
                val value = obj.get(key) as? String ?: error("Invalid device report.")
                require(value.isNotBlank() && value.length <= limit) { "Invalid device report." }
                return value
            }
            val array = json.getJSONArray("apps")
            require(array.length() <= 256) { "Too many apps in device report." }
            val apps = (0 until array.length()).map { index ->
                val app = array.getJSONObject(index)
                val permissions = app.getJSONArray("permissions")
                require(permissions.length() <= 256) { "Too many permissions in device report." }
                RemoteApp(text(app, "packageName", 256), text(app, "label", 160),
                    (0 until permissions.length()).map {
                        val permission = permissions.getJSONObject(it)
                        val status = text(permission, "status", 16)
                        require(status in setOf("granted", "not_granted", "unknown")) { "Invalid permission status." }
                        RemotePermission(text(permission, "name", 256), status)
                    })
            }
            val total = json.getInt("totalVisibleApps")
            val observedAt = json.getLong("observedAt")
            require(total in apps.size..10000 && observedAt >= 0 && apps.map { it.packageName }.distinct().size == apps.size) {
                "Invalid device coverage."
            }
            return RemoteDeviceInventory(text(json, "deviceName", 80), text(json, "androidVersion", 40), observedAt, total, apps)
        }
    }
}

/** Runs only on the sharing device, after its user approves. No writes or privileged permissions. */
@Suppress("DEPRECATION")
internal fun collectRemoteDeviceInventory(context: Context): RemoteDeviceInventory {
    val pm = context.packageManager
    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        .sortedBy { it.packageName }
    val apps = packages.take(256).map { pkg ->
        val flags = pkg.requestedPermissionsFlags
        val permissions = pkg.requestedPermissions.orEmpty()
        require(permissions.size <= 256) { "An app has too many permissions to share." }
        RemoteApp(pkg.packageName.take(256),
            (pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName).take(160).ifBlank { pkg.packageName },
            permissions.mapIndexed { index, permission ->
                val status = flags?.getOrNull(index)?.let {
                    if (it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) "granted" else "not_granted"
                } ?: "unknown"
                RemotePermission(permission.take(256), status)
            })
    }
    return RemoteDeviceInventory(Build.MODEL.take(80).ifBlank { "Android device" },
        Build.VERSION.RELEASE.take(40).ifBlank { "Unknown" }, System.currentTimeMillis(), packages.size, apps)
}
