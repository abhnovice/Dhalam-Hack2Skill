package com.dhalam.dhalam

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AtomicFile
import android.view.WindowManager
import java.io.File
import java.util.function.Consumer

/** The journal deliberately lives outside Android's backup directory. */
class ToolsStorage(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "security-journal.json"))
    fun load(): String? = if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) file.openRead().bufferedReader().use { it.readText() } else null
    fun save(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= 2_000_000) { "History is too large" }
        val out = file.startWrite()
        try { out.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
    }
}

class CaptureMonitor(private val activity: Activity, private val changed: (String) -> Unit) {
    var status = "Unavailable"
        private set
    private var registered = false
    private val callback = Consumer<Int> { state -> update(state) }
    private fun update(state: Int) {
        status = if (state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE) "Active" else "Inactive"
        changed(status)
    }
    fun start() {
        if (Build.VERSION.SDK_INT >= 35 && !registered) {
            try {
                val state = activity.windowManager.addScreenRecordingCallback(activity.mainExecutor, callback)
                registered = true
                update(state)
            } catch (_: Exception) { status = "Unavailable"; changed(status) }
        }
    }
    fun stop() {
        if (Build.VERSION.SDK_INT >= 35 && registered) {
            try { activity.windowManager.removeScreenRecordingCallback(callback) } catch (_: Exception) { }
        }
        registered = false
        status = "Unavailable"
        changed(status)
    }
}

fun shareSecurityReport(activity: Activity, text: String) {
    require(text.length <= 200_000) { "Report is too large to share" }
    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Dhalam security report")
        putExtra(Intent.EXTRA_TEXT, text)
    }, "Share security report"))
}

/** Opt-in periodic access review. Never reads screen contents or sends network data. */
class LocalAccessWorker(context: Context, params: androidx.work.WorkerParameters) :
    androidx.work.Worker(context, params) {
    companion object {
        private const val WORK = "dhalam-local-access-review"
        private const val PREFS = "dhalam-background-opt-in"
        private val lock = Any()
        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun baseline(context: Context) = AtomicFile(File(context.noBackupFilesDir, "background-access.json"))

        fun configure(context: Context, enabled: Boolean) = synchronized(lock) {
            check(prefs(context).edit().putBoolean("enabled", enabled).commit())
            val manager = androidx.work.WorkManager.getInstance(context)
            if (enabled) {
                manager.enqueueUniquePeriodicWork(WORK, androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    androidx.work.PeriodicWorkRequest.Builder(LocalAccessWorker::class.java, 15, java.util.concurrent.TimeUnit.MINUTES)
                        .setConstraints(androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
            } else {
                manager.cancelUniqueWork(WORK)
                baseline(context).delete()
                prefs(context).edit().remove("checkedAt").remove("changes").apply()
                context.getSystemService(android.app.NotificationManager::class.java)?.cancel(1002)
            }
        }
        fun status(context: Context): Map<String, Any> = synchronized(lock) {
            mapOf("enabled" to prefs(context).getBoolean("enabled", false),
                "checkedAt" to prefs(context).getLong("checkedAt", 0),
                "changes" to prefs(context).getInt("changes", 0))
        }
    }

    override fun doWork(): Result {
        if (!prefs(applicationContext).getBoolean("enabled", false) || isStopped) return Result.success()
        val observation = try { collectSecurityObservation(applicationContext) }
            catch (_: Exception) { return Result.retry() }
        return synchronized(lock) {
            if (!prefs(applicationContext).getBoolean("enabled", false) || isStopped) return@synchronized Result.success()
            val file = baseline(applicationContext)
            val previous = try {
                if (file.baseFile.exists()) org.json.JSONObject(file.openRead().bufferedReader().use { it.readText() }) else org.json.JSONObject()
            } catch (_: Exception) { org.json.JSONObject() }
            val next = org.json.JSONObject(previous.toString())
            var changes = 0
            observation.services?.let { services ->
                val ids = services.map { normalizedServiceId(it.id) }
                if (ids.none { it == null }) {
                    val current = ids.filterNotNull().toSet()
                    previous.optJSONArray("services")?.let { old ->
                        val prior = (0 until old.length()).map { old.getString(it) }.toSet()
                        changes += (current - prior).size + (prior - current).size
                    }
                    next.put("services", org.json.JSONArray(current.toList()))
                }
            }
            observation.permissionInventory?.let { inventory ->
                val current = org.json.JSONObject()
                val old = previous.optJSONObject("permissions")
                inventory.permissions.forEach { permission -> permission.apps.forEach { app ->
                    app.granted?.let { granted ->
                        val key = "${app.packageName}|${permission.name}"
                        current.put(key, granted)
                        // Only known-to-known transitions count; visibility changes do not.
                        if (old?.has(key) == true && old.getBoolean(key) != granted) changes++
                    }
                } }
                next.put("permissions", current)
            }
            try {
                val output = file.startWrite()
                try { output.write(next.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
                catch (error: Exception) { file.failWrite(output); throw error }
                check(prefs(applicationContext).edit().putLong("checkedAt", observation.observedAt).putInt("changes", changes).commit())
            } catch (_: Exception) { return@synchronized Result.retry() }
            if (changes > 0) notifyChange(changes)
            Result.success()
        }
    }

    @Suppress("DEPRECATION")
    private fun notifyChange(changes: Int) {
        val context = applicationContext
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(android.app.NotificationChannel("access_changes", "Local access changes", android.app.NotificationManager.IMPORTANCE_LOW))
            android.app.Notification.Builder(context, "access_changes")
        } else android.app.Notification.Builder(context)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            builder.setContentIntent(android.app.PendingIntent.getActivity(context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
        }
        try {
            manager.notify(1002, builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Dhalam: review access changes")
                .setContentText("$changes changes observed. Open Dhalam to review; changes do not prove abuse.")
                .setVisibility(android.app.Notification.VISIBILITY_PRIVATE).setAutoCancel(true).build())
        } catch (_: SecurityException) { /* Notifications can be revoked while a check runs. */ }
    }
}
