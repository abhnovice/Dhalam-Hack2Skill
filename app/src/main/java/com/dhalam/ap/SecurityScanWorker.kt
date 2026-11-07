package com.dhalam.ap

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

private const val CHANNEL_ID = "dhalam_quiet_changes"
private const val NOTIFICATION_ID = 1001
private const val LOG_TAG = "DhalamBackground"

class SecurityScanWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val observation = collectSecurityObservation(context)
        val prefs = context.getSharedPreferences(
            "dhalam_scoped_baseline",
            Context.MODE_PRIVATE
        )
        val update = prefs.edit()
        val changes = mutableListOf<String>()

        val ids = observation.services?.map { normalizedServiceId(it.id) }
        if (ids != null && ids.none { it == null }) {
            val current = ids.filterNotNull().toSet()
            prefs.getStringSet("service_ids", null)?.let { previous ->
                val added = (current - previous).size
                val removed = (previous - current).size
                if (added + removed > 0) {
                    changes.add("Accessibility services: $added added, $removed removed.")
                }
            }
            update.putStringSet("service_ids", current)
        }

        observation.permissionInventory?.let { inventory ->
            val granted = mutableSetOf<String>()
            val notGranted = mutableSetOf<String>()

            inventory.permissions.forEach { report ->
                report.apps.forEach { app ->
                    val key = "${app.packageName}|${report.name}"
                    when (app.granted) {
                        true -> granted.add(key)
                        false -> notGranted.add(key)
                        null -> Unit
                    }
                }
            }

            val previousGranted = prefs.getStringSet("granted", null)
            val previousNotGranted = prefs.getStringSet("not_granted", null)
            if (previousGranted != null && previousNotGranted != null) {
                val added = granted.intersect(previousNotGranted).size
                val removed = notGranted.intersect(previousGranted).size
                if (added + removed > 0) {
                    changes.add("App permissions: $added newly granted, $removed no longer granted.")
                }
            }

            update.putStringSet("granted", granted)
            update.putStringSet("not_granted", notGranted)
        }

        if (!update.commit()) return Result.retry()

        Log.i(LOG_TAG, "Scan at ${observation.observedAt}. Changes: ${changes.size}")
        if (changes.isNotEmpty()) postNotification(context, changes.joinToString("\n"))

        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun postNotification(context: Context, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Device setting changes",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context).setPriority(Notification.PRIORITY_LOW)
        }

        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Dhalam observed a change")
            .setContentText(message.replace('\n', ' '))
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            Log.i(LOG_TAG, "Notification permission unavailable.")
        }
    }
}