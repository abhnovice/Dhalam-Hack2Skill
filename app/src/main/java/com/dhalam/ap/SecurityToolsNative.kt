package com.dhalam.ap

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
