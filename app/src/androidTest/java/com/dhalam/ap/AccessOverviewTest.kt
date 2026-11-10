package com.dhalam.ap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccessOverviewTest {
    @get:Rule val compose = createComposeRule()

    private fun snapshot(time: Long = 10) = SecurityObservation(
        time, emptyList(), emptyList(), emptyList(), null,
        accessibilitySettings = listOf(AccessibilitySetting("incall_power_button_behavior",
            "Press Power button to end calls", "Interaction & calls", "Check in Settings", "Read only")),
        apps = listOf(
            ObservedApp("demo.bank", "Demo Bank", true, "Banking & finance", "Suggested category", false),
            ObservedApp("demo.system", "Demo Tool", false, "System & device tools", "System metadata", true)
        )
    )

    @Test fun settingsAndAppsRemainAvailableWithoutAnAiReport() {
        compose.setContent { MaterialTheme { AccessOverview(snapshot(), false, null, {}, { error("Offline") }) } }
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("Press Power button to end calls"))
        compose.onNodeWithText("Press Power button to end calls").assertIsDisplayed()
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Apps you see"))
        compose.onNodeWithContentDescription("Apps you see").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("Banking & finance"))
        compose.onNodeWithText("Banking & finance").performClick()
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("Demo Bank"))
        compose.onNodeWithText("Demo Bank").assertIsDisplayed()
        compose.onNodeWithTag("apps-heading:false").assertDoesNotExist()
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Apps you don't see"))
        compose.onNodeWithContentDescription("Apps you don't see").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("access-list").performScrollToNode(hasTestTag("apps-heading:false"))
        compose.onNodeWithTag("apps-heading:false").assertIsDisplayed()
    }

    @Test fun settingsViewIsOptionalAndAppsKeepTheirIcons() {
        compose.setContent { MaterialTheme { AccessOverview(snapshot(), false, null, {}, { error("Offline") }) } }
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Accessibility settings view"))
        compose.onNodeWithContentDescription("Accessibility settings view").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Press Power button to end calls").assertDoesNotExist()
        compose.onNodeWithText("Accessibility services").assertDoesNotExist()
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Apps you see"))
        compose.onNodeWithContentDescription("Apps you see").performClick().assertIsOn()
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("Banking & finance"))
        compose.onNodeWithText("Banking & finance").performClick()
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("Demo Bank"))
        compose.onNodeWithTag("app-icon:demo.bank", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Demo Bank").performClick()
        compose.onNodeWithText("Suggested category").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Accessibility settings view"))
        compose.onNodeWithContentDescription("Accessibility settings view").performClick().assertIsOn()
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("Press Power button to end calls"))
        compose.onNodeWithText("Press Power button to end calls").assertIsDisplayed()
    }

    @Test fun nonLauncherAppsCanBeShownWithoutLauncherApps() {
        compose.setContent { MaterialTheme { AccessOverview(snapshot(), false, null, {}, { error("Offline") }) } }
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Apps you don't see"))
        compose.onNodeWithContentDescription("Apps you don't see").performClick().assertIsOn()
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("System & device tools"))
        compose.onNodeWithText("System & device tools").performClick()
        compose.onNodeWithTag("access-list").performScrollToNode(hasText("Demo Tool"))
        compose.onNodeWithText("Demo Tool").assertIsDisplayed()
        compose.onNodeWithTag("apps-heading:true").assertDoesNotExist()
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Apps you see"))
        compose.onNodeWithContentDescription("Apps you see").assertIsOff()
    }

    @Test fun reportRequiresConsentAndBecomesStaleAfterNewScan() {
        val observed = mutableStateOf(snapshot())
        var calls = 0
        val answer = "A synthetic accessibility report for this test."
        compose.setContent {
            MaterialTheme { AccessOverview(observed.value, false, null, {}, { calls++; answer }) }
        }
        compose.onNodeWithText("Explain my settings").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Generate report").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(answer).assertExists()
        compose.runOnIdle { assertEquals(1, calls); observed.value = snapshot(20) }
        compose.onNodeWithText("Your scan has changed. Generate an updated report.").assertExists()
    }
}
