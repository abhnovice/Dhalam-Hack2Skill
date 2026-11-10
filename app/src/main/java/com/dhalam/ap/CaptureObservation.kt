package com.dhalam.ap

data class CaptureObservation(
    val recording: String = "Unavailable",
    val screenshotsAvailable: Boolean = false,
    val lastScreenshotAt: Long? = null
)

internal fun recordingStatus(visible: Int): String = when (visible) {
    1 -> "Active"
    0 -> "Inactive"
    else -> "Unavailable"
}
