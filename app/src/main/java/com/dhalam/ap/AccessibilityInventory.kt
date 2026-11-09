package com.dhalam.ap

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.CaptioningManager
import java.util.Locale

data class AccessibilitySetting(
    val id: String,
    val label: String,
    val group: String,
    val status: String,
    val explanation: String
)

data class ObservedApp(
    val packageName: String,
    val label: String,
    val launcherVisible: Boolean?,
    val category: String,
    val categorySource: String,
    val systemApp: Boolean
)

internal fun toggleSettingStatus(raw: String?): String = when (raw) {
    "0" -> "Reported off"
    "1" -> "Reported on"
    else -> "Check in Settings"
}

internal fun powerButtonStatus(raw: String?): String = when (raw?.toIntOrNull()) {
    1 -> "Reported off — power button turns off the screen"
    2, 3 -> "Reported on — power button ends calls"
    else -> "Check in Settings"
}

/** Read-only, best-effort AOSP settings. Missing and restricted values are never treated as off.
 * OEM-specific options remain reviewable through Android Settings; there is no universal API
 * enumerating every manufacturer's accessibility page.
 */
fun collectAccessibilitySettings(context: Context): List<AccessibilitySetting> {
    fun secure(key: String): String? = try {
        Settings.Secure.getString(context.contentResolver, key)
    } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
    fun system(key: String): String? = try {
        Settings.System.getString(context.contentResolver, key)
    } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
    fun bool(key: String, label: String, group: String, note: String) =
        AccessibilitySetting(key, label, group, toggleSettingStatus(secure(key)), note)
    fun number(key: String, label: String, group: String, unit: String) =
        AccessibilitySetting(key, label, group,
            secure(key)?.toIntOrNull()?.takeIf { it >= 0 }?.let { "$it $unit" } ?: "Check in Settings",
            "A preference value, not evidence of actual use.")
    val manager = context.getSystemService(AccessibilityManager::class.java)
    val touch = try { manager?.isTouchExplorationEnabled } catch (_: SecurityException) { null }
    val captioning = try {
        context.getSystemService(CaptioningManager::class.java)?.isEnabled
    } catch (_: SecurityException) { null }
    fun status(value: Boolean?) = when (value) {
        true -> "Reported on"; false -> "Reported off"; null -> "Check in Settings"
    }
    val animationScales = listOf(Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.ANIMATOR_DURATION_SCALE).map { key ->
        try { Settings.Global.getString(context.contentResolver, key)?.toFloatOrNull() }
        catch (_: SecurityException) { null }
    }
    val animationStatus = when {
        animationScales.any { it == null } -> "Check in Settings"
        animationScales.all { it == 0f } -> "Reported on — system animations removed"
        animationScales.all { requireNotNull(it) > 0f } -> "Reported off — system animations allowed"
        else -> "Mixed animation preferences — review in Settings"
    }
    return listOf(
        bool("accessibility_enabled", "Accessibility services", "Services & shortcuts",
            "Enabled services can assist with reading or interaction. Review each service and its capabilities."),
        AccessibilitySetting("touch_exploration_enabled", "Explore by touch", "Services & shortcuts",
            status(touch), "Supports screen readers. Enabling it is not evidence of a scam."),
        bool("accessibility_shortcut_on_lock_screen", "Accessibility shortcut on lock screen", "Services & shortcuts",
            "A shortcut can make selected accessibility tools available from the lock screen."),
        AccessibilitySetting("accessibility_shortcut_target_service", "Accessibility shortcut targets", "Services & shortcuts",
            if (secure("accessibility_shortcut_target_service").isNullOrBlank()) "Check in Settings" else "Configured — review targets in Settings",
            "Configured targets are not proof that the shortcut or service is currently active."),
        AccessibilitySetting("accessibility_button_targets", "Accessibility button targets", "Services & shortcuts",
            if (secure("accessibility_button_targets").isNullOrBlank()) "Check in Settings" else "Configured — review targets in Settings",
            "Review the tools assigned to the accessibility button."),
        AccessibilitySetting("incall_power_button_behavior", "Press Power button to end calls", "Interaction & calls",
            powerButtonStatus(secure("incall_power_button_behavior")),
            "A call-control convenience. It does not grant another app access to your calls."),
        bool("accessibility_autoclick_enabled", "Automatic click / dwell timing", "Interaction & calls",
            "Automatically clicks after the pointer rests. Useful for motor accessibility."),
        number("accessibility_autoclick_delay", "Automatic click delay", "Interaction & calls", "ms"),
        number("long_press_timeout", "Touch & hold delay", "Interaction & calls", "ms"),
        number("accessibility_interactive_ui_timeout_ms", "Time to take action — controls", "Interaction & calls", "ms"),
        number("accessibility_non_interactive_ui_timeout_ms", "Time to take action — messages", "Interaction & calls", "ms"),
        bool("accessibility_display_magnification_enabled", "Magnification — triple-tap gesture", "Display & reading",
            "Reports the gesture preference, not whether the screen is currently magnified."),
        bool("accessibility_display_magnification_navbar_enabled", "Magnification — legacy navigation shortcut", "Display & reading",
            "Newer Android versions use accessibility button targets instead; this legacy value may be unavailable."),
        bool("accessibility_display_inversion_enabled", "Color inversion", "Display & reading",
            "Changes how colors appear. It does not give another app access to your screen."),
        bool("accessibility_display_daltonizer_enabled", "Color correction", "Display & reading",
            "Adjusts colors for visual accessibility; this is not a security warning."),
        bool("high_text_contrast_enabled", "High-contrast text", "Display & reading",
            "Improves text visibility. Availability varies by Android version."),
        bool("accessibility_large_pointer_icon", "Large mouse pointer", "Display & reading",
            "Makes a connected pointer easier to see."),
        bool("reduce_bright_colors_activated", "Extra dim", "Display & reading",
            "Reduces display brightness beyond the usual brightness slider."),
        AccessibilitySetting("font_scale", "Font size", "Display & reading",
            "Scale ×" + String.format(Locale.ROOT, "%.2f", context.resources.configuration.fontScale),
            "Current font scale reported by Android for this app."),
        AccessibilitySetting("font_weight", "Bold text", "Display & reading",
            if (Build.VERSION.SDK_INT >= 31) context.resources.configuration.fontWeightAdjustment
                .takeUnless { it == Int.MAX_VALUE }?.let { "Font weight adjustment: $it" } ?: "Check in Settings"
            else "Check in Settings", "Current font-weight adjustment; 0 means the normal weight."),
        AccessibilitySetting("remove_animations", "Remove animations", "Display & reading", animationStatus,
            "Reports system animation preferences, not whether every app follows them."),
        AccessibilitySetting("display_size", "Display size", "Display & reading", "Check in Settings",
            "Display size and manufacturer-specific visual options must be reviewed in Android Settings."),
        AccessibilitySetting("captions", "Caption preferences", "Hearing & audio", status(captioning),
            "Reports Android caption preference; individual apps may have separate controls."),
        AccessibilitySetting("master_mono", "Mono audio", "Hearing & audio",
            toggleSettingStatus(system("master_mono")),
            "Combines left and right audio. It does not grant microphone access."),
        AccessibilitySetting("master_balance", "Left/right audio balance", "Hearing & audio",
            system("master_balance")?.toFloatOrNull()?.takeIf { it in -1f..1f }?.let {
                when { it == 0f -> "Centered"; it < 0 -> "Left: ${(-it * 100).toInt()}%"; else -> "Right: ${(it * 100).toInt()}%" }
            } ?: "Check in Settings", "Balance preference for audio output, not microphone access."),
        bool("odi_captions_enabled", "Live Caption", "Hearing & audio",
            "Availability and language support vary by device."),
        AccessibilitySetting("camera_flash_notification", "Camera flash notifications", "Hearing & audio",
            toggleSettingStatus(system("camera_flash_notification")), "Uses the camera flash for alerts."),
        AccessibilitySetting("screen_flash_notification", "Screen flash notifications", "Hearing & audio",
            toggleSettingStatus(system("screen_flash_notification")), "Flashes the display for alerts."),
        AccessibilitySetting("haptic_feedback_enabled", "Touch vibration / haptic feedback", "Interaction & calls",
            toggleSettingStatus(system(Settings.System.HAPTIC_FEEDBACK_ENABLED)),
            "Touch-feedback preference. Ring, notification and accessibility vibration controls may differ."),
        AccessibilitySetting("hearing_aids", "Hearing aids & audio devices", "Hearing & audio", "Check in Settings",
            "Device pairing, live captions and manufacturer audio features are not fully exposed to Dhalam."),
        number("tts_default_rate", "Text-to-speech rate", "Speech", "%"),
        number("tts_default_pitch", "Text-to-speech pitch", "Speech", "%"),
        AccessibilitySetting("voice_access", "Voice Access & Switch Access", "Services & shortcuts", "Review service list below",
            "Installed and enabled services are listed separately. Installed does not mean active."),
        AccessibilitySetting("vendor_options", "Other device accessibility options", "Device-specific options", "Check in Settings",
            "Review your phone's accessibility page for options such as vibration, flash notifications, live captions and gesture controls.")
    ).distinctBy { it.id }
}

internal fun appCategory(category: Int, packageName: String, label: String, system: Boolean): Pair<String, String> {
    // Android's manifest categories have no finance value. Mark this local hint explicitly.
    val finance = Regex("(?i)(^|[ ._-])(bank|banking|finance|financial|paytm|phonepe|gpay|paypal)([ ._-]|$)")
    val knownFinance = packageName in setOf("com.google.android.apps.nbu.paisa.user",
        "com.sbi.lotusintouch", "com.csam.icici.bank.imobile", "com.snapwork.hdfc")
    if (knownFinance || finance.containsMatchIn("$packageName $label")) {
        return "Banking & finance" to "Suggested from app name/package; not verified"
    }
    val declared = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Gaming"
        ApplicationInfo.CATEGORY_AUDIO -> "Music & audio"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Photos & images"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social & communication"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps & navigation"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
        else -> null
    }
    return if (declared != null) declared to "Category declared in Android app metadata"
    else (if (system) "System & device tools" else "Other / uncategorized") to
        (if (system) "System-app flag; no declared category" else "No category declared")
}

@Suppress("DEPRECATION")
fun collectObservedApps(pm: PackageManager): List<ObservedApp>? {
    val launcherPackages = try {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .mapNotNull { it.activityInfo?.packageName }.toSet()
    } catch (_: SecurityException) { null }
    val installed = try { pm.getInstalledApplications(0) } catch (_: SecurityException) { return null }
    return installed.mapNotNull { app ->
        try {
            val label = app.loadLabel(pm).toString()
            val system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val category = appCategory(app.category, app.packageName, label, system)
            ObservedApp(app.packageName, label, launcherPackages?.contains(app.packageName),
                category.first, category.second, system)
        } catch (_: SecurityException) { null } catch (_: PackageManager.NameNotFoundException) { null }
    }.distinctBy { it.packageName }.sortedWith(compareBy<ObservedApp> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
}
