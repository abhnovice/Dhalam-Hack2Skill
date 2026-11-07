package com.dhalam.dhalam

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var tools: MethodChannel? = null
    private val capture by lazy { CaptureMonitor(this) { state -> tools?.invokeMethod("captureChanged", state) } }
    override fun onStart() { super.onStart(); capture.start() }
    override fun onStop() { capture.stop(); super.onStop() }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        val storage = ToolsStorage(applicationContext)
        tools = MethodChannel(messenger, "com.dhalam.ap/tools")
        tools!!.setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "loadJournal" -> result.success(storage.load())
                    "saveJournal" -> { storage.save(call.arguments as String); result.success(null) }
                    "backgroundStatus" -> result.success(LocalAccessWorker.status(applicationContext))
                    "setBackgroundChecks" -> {
                        LocalAccessWorker.configure(applicationContext, call.arguments == true)
                        result.success(LocalAccessWorker.status(applicationContext))
                    }
                    "captureStatus" -> result.success(capture.status)
                    "shareReport" -> { shareSecurityReport(this, call.arguments as String); result.success(null) }
                    "openPrivacySettings" -> openSettings(Intent(Settings.ACTION_PRIVACY_SETTINGS), result)
                    else -> result.notImplemented()
                }
            } catch (error: Exception) { result.error("TOOLS_FAILED", error.message, null) }
        }
        MethodChannel(messenger, "com.dhalam.ap/security").setMethodCallHandler { call, result ->
            when (call.method) {
                "collectSecurityObservation" -> Thread {
                    try {
                        val observation = collectSecurityObservation(applicationContext).toChannelMap()
                        runOnUiThread { result.success(observation) }
                    } catch (error: Exception) {
                        runOnUiThread { result.error("SCAN_FAILED", error.message, null) }
                    }
                }.start()
                "openAccessibilitySettings" -> openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), result)
                "openDeveloperOptions" -> openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), result)
                "openAppSettings" -> openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")), result)
                else -> result.notImplemented()
            }
        }
        MethodChannel(messenger, "com.dhalam.ap/notifications").setMethodCallHandler { call, result ->
            when (call.method) {
                "areNotificationsEnabled" -> result.success(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                    else true)
                "enableAlerts" -> {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    openSettings(intent, result)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun openSettings(intent: Intent, result: MethodChannel.Result) {
        try {
            startActivity(intent)
            result.success(null)
        } catch (error: Exception) {
            result.error("SETTINGS_UNAVAILABLE", error.message, null)
        }
    }
}

private fun ObservedAccessibilityService.toChannelMap(): Map<String, Any?> = mapOf(
    "name" to name, "id" to id, "capabilities" to capabilities)

private fun SecurityObservation.toChannelMap(): Map<String, Any?> = mapOf(
    "observedAt" to observedAt,
    "services" to services?.map { it.toChannelMap() },
    "installedServices" to installedServices?.map { it.toChannelMap() },
    "deviceSignals" to deviceSignals.map { mapOf("label" to it.label, "status" to it.status) },
    "developerSignals" to developerSignals.map { mapOf("label" to it.label, "status" to it.status) },
    "permissionInventory" to permissionInventory?.let { inventory -> mapOf(
        "packageCount" to inventory.packageCount,
        "permissions" to inventory.permissions.map { permission -> mapOf(
            "name" to permission.name,
            "apps" to permission.apps.map { app -> mapOf(
                "packageName" to app.packageName, "label" to app.label, "granted" to app.granted)
            }) }) })
