package com.dhalam.ap

import com.dhalam.ap.data.SecurityStore
import kotlinx.coroutines.CancellationException
import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val observation = mutableStateOf<SecurityObservation?>(null)
    private val scanning = mutableStateOf(false)
    private val scanError = mutableStateOf<String?>(null)
    private val notificationsEnabled = mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshNotificationStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SecurityDashboard(
                observation = observation.value,
                scanning = scanning.value,
                scanError = scanError.value,
                notificationsEnabled = notificationsEnabled.value,
                onScan = ::scan,
                onEnableAlerts = ::enableAlerts,
                onOpenAccessibility = {
                    openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenDeveloperOptions = {
                    openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
            )
        }

        val request = PeriodicWorkRequestBuilder<SecurityScanWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "dhalam-security-scan",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationStatus()
        scan()
    }

    private fun scan() {
        if (scanning.value) return
        scanning.value = true
        scanError.value = null

        lifecycleScope.launch {
            try {
                observation.value = withContext(Dispatchers.IO) {
                    collectSecurityObservation(applicationContext).also { snapshot ->
                        val repository = SecurityStore.repository(applicationContext)
                        repository.saveObservation(snapshot)
                        Log.i("DhalamStorage", "Foreground scan saved. Stored facts: ${repository.count()}")
                    }
                }
            } catch (error: SecurityException) {
                Log.w("DhalamScan", "Android blocked a scan read.", error)
                scanError.value = "Android blocked a scan read. Try Scan now again."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("DhalamStorage", "Could not save scan.", error)
                scanError.value = "Could not complete and save the scan. Try Scan now again."
            } finally {
                scanning.value = false
            }
        }
    }

    private fun refreshNotificationStatus() {
        notificationsEnabled.value = getSystemService(NotificationManager::class.java)
            ?.areNotificationsEnabled() == true
    }

    private fun enableAlerts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("dhalam_permissions", MODE_PRIVATE)
            if (!prefs.getBoolean("asked_for_notifications", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                prefs.edit().putBoolean("asked_for_notifications", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        }
        openSettings(intent)
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}