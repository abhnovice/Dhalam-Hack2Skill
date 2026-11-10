package com.dhalam.ap

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsToolsExperienceTest {
    @get:Rule val compose = createComposeRule()
    private val observed = SecurityObservation(10, emptyList(), emptyList(), emptyList(), null,
        deviceSignals = listOf(
            DeveloperSignal("Device lock", "Not configured — set a screen lock"),
            DeveloperSignal("Biometric authentication", "Unavailable — no hardware"),
            DeveloperSignal("Operating system", "Android 17 — review Software Update")))

    @Test fun swipeNavigatesThroughSettingsAndToolsBothDirections() {
        compose.setContent {
            SecurityDashboard(observed, false, null, true, {}, {}, {}, {},
                AskUiState(signedIn = true), {}, {}, {}, {}, {})
        }
        compose.onNodeWithContentDescription("Ask Dhalam", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("dashboard-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Device snapshot").assertIsDisplayed()
        val lock = compose.onNode(hasText("Device lock") and hasAnyAncestor(hasTestTag("dashboard-list:Developer"))).fetchSemanticsNode().boundsInRoot
        val biometric = compose.onNode(hasText("Biometric authentication") and hasAnyAncestor(hasTestTag("dashboard-list:Developer"))).fetchSemanticsNode().boundsInRoot
        assertTrue(lock.left < biometric.left)
        assertEquals(lock.top, biometric.top, 1f)
        compose.onNodeWithTag("dashboard-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Security overview").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-pager").performTouchInput { swipeRight() }
        compose.onNodeWithText("Device snapshot").assertIsDisplayed()
    }

    @Test fun recordingAndScreenshotAppearAsSeparateSignals() {
        compose.setContent {
            SecurityDashboard(observed, false, null, true, {}, {}, {}, {},
                AskUiState(signedIn = true), {}, {}, {}, {}, {},
                captureObservation = CaptureObservation("Active", true, 1700000000000))
        }
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("dashboard-list:Developer").performScrollToNode(hasText("Screen capture"))
        compose.onNodeWithText("Recording").assertExists()
        compose.onNodeWithText("Active").assertExists()
        compose.onNodeWithText("Screenshot").assertExists()
        compose.onNodeWithText("Detected").assertExists()
    }

    @Test fun realCaptureObserversRegisterAndStopWithoutStaleState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val monitor = CaptureMonitor(activity) {}
                try {
                    monitor.start()
                    monitor.start() // Idempotent lifecycle callbacks.
                    if (Build.VERSION.SDK_INT >= 35) assertTrue(monitor.observation.recording in listOf("Active", "Inactive"))
                    if (Build.VERSION.SDK_INT >= 34) assertTrue(monitor.observation.screenshotsAvailable)
                } finally { monitor.stop() }
                assertEquals("Unavailable", monitor.observation.recording)
                assertFalse(monitor.observation.screenshotsAvailable)
                monitor.stop()
            }
        }
    }
}
