package com.dhalam.ap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AtomicFile
import android.util.Log
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

/** Android reports captures of this visible activity, not arbitrary captures of other apps. */
class CaptureMonitor(private val activity: Activity, private val changed: (CaptureObservation) -> Unit) {
    var observation = CaptureObservation()
        private set
    private var started = false
    private var recordingCallback: Consumer<Int>? = null
    private var screenshotCallback: Activity.ScreenCaptureCallback? = null
    private var generation = 0

    fun start() {
        if (started) return
        started = true
        val token = ++generation
        if (Build.VERSION.SDK_INT >= 35) {
            val listener = Consumer<Int> { state ->
                if (started && token == generation) {
                    observation = observation.copy(recording = recordingStatus(state))
                    changed(observation)
                }
            }
            try {
                val initial = activity.windowManager.addScreenRecordingCallback(activity.mainExecutor, listener)
                recordingCallback = listener
                observation = observation.copy(recording = recordingStatus(initial))
            } catch (error: RuntimeException) {
                observation = observation.copy(recording = "Unavailable")
                Log.w("DhalamCapture", "Recording observer unavailable: ${error.javaClass.simpleName}")
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val listener = Activity.ScreenCaptureCallback {
                if (started && token == generation) {
                    observation = observation.copy(lastScreenshotAt = System.currentTimeMillis())
                    changed(observation)
                    Log.i("DhalamCapture", "Screenshot observed.")
                }
            }
            try {
                activity.registerScreenCaptureCallback(activity.mainExecutor, listener)
                screenshotCallback = listener
                observation = observation.copy(screenshotsAvailable = true)
            } catch (error: RuntimeException) {
                observation = observation.copy(screenshotsAvailable = false)
                Log.w("DhalamCapture", "Screenshot observer unavailable: ${error.javaClass.simpleName}")
            }
        }
        changed(observation)
    }

    fun stop() {
        started = false
        generation++ // Ignore callbacks queued before unregistering.
        if (Build.VERSION.SDK_INT >= 35) recordingCallback?.let {
            try { activity.windowManager.removeScreenRecordingCallback(it) }
            catch (_: RuntimeException) { Log.w("DhalamCapture", "Recording observer cleanup failed.") }
        }
        if (Build.VERSION.SDK_INT >= 34) screenshotCallback?.let {
            try { activity.unregisterScreenCaptureCallback(it) }
            catch (_: RuntimeException) { Log.w("DhalamCapture", "Screenshot observer cleanup failed.") }
        }
        recordingCallback = null
        screenshotCallback = null
        observation = observation.copy(recording = "Unavailable", screenshotsAvailable = false)
        changed(observation)
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
