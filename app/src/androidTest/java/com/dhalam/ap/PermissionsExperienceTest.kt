package com.dhalam.ap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PermissionsExperienceTest {
    @get:Rule val compose = createComposeRule()
    private val camera = "android.permission.CAMERA"
    private val inventory = PermissionInventory(5, listOf(PermissionReport(camera, listOf(
        PermissionApp("demo.camera", "Demo", true),
        PermissionApp("demo.camera", "Demo duplicate", true),
        PermissionApp("demo.other", "Other", false)
    ))))

    @Test fun chatIsAboveDeviceAccessAndExpandsToGeminiExperience() {
        val observation = SecurityObservation(10, emptyList(), emptyList(), emptyList(), inventory)
        compose.setContent {
            SecurityDashboard(observation, false, null, true, {}, {}, {}, {},
                AskUiState(signedIn = true), {}, {}, {}, {}, {})
        }
        compose.onNodeWithContentDescription("Ask Dhalam", useUnmergedTree = true).performClick()
        val chat = compose.onNodeWithTag("device-chat-toggle").fetchSemanticsNode().boundsInRoot
        val access = compose.onNodeWithText("Device access").fetchSemanticsNode().boundsInRoot
        assertTrue(chat.top < access.top)
        compose.onNodeWithTag("device-chat-toggle").performClick()
        compose.onNodeWithText("Google Gemini").assertIsDisplayed()
        compose.onNodeWithText("What would you like to understand?").assertExists()
    }

    @Test fun accessAndAskPushNavigationPreservesViewSwitches() {
        val observation = SecurityObservation(10, emptyList(), emptyList(), emptyList(), inventory)
        compose.setContent {
            SecurityDashboard(observation, false, null, true, {}, {}, {}, {},
                AskUiState(signedIn = true), {}, {}, {}, {}, {})
        }
        compose.onNodeWithContentDescription("Access", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Accessibility settings view"))
        compose.onNodeWithContentDescription("Accessibility settings view").performClick().assertIsOff()
        compose.onNodeWithContentDescription("Ask Dhalam", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("device-chat-toggle").assertIsDisplayed().performClick()
        compose.onNodeWithText("Google Gemini").assertIsDisplayed()
        compose.onNodeWithContentDescription("Access", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("access-list").performScrollToNode(hasContentDescription("Accessibility settings view"))
        compose.onNodeWithContentDescription("Accessibility settings view").assertIsOff()
    }

    @Test fun sensitivePermissionsUseTwoColumnsAndKeepTapFiltering() {
        var selected = ""
        compose.setContent { MaterialTheme { SensitivePermissionChart(inventory) { selected = it } } }
        val first = compose.onNodeWithTag("sensitive:$camera").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("sensitive:android.permission.RECORD_AUDIO").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(first.right <= second.left)
        compose.onNodeWithText("1 app").assertExists()
        compose.onAllNodesWithText("Not observed").assertCountEquals(4)
        compose.onNodeWithTag("sensitive:$camera").performClick()
        compose.runOnIdle { assertEquals(camera, selected) }
    }

    @Test fun newChatClearsTranscriptAndSuggestionsFillComposer() {
        val result = AskResult("Your camera access needs review.", emptyList(), emptyList(), emptyList(), 10, 2)
        val state = mutableStateOf(AskUiState(signedIn = true,
            conversation = listOf(AskChatTurn("Who has camera access?", result))))
        compose.setContent {
            MaterialTheme {
                DeviceChat(state.value, false, 10, { state.value = state.value.copy(question = it) }, {},
                    { state.value = state.value.copy(conversation = emptyList(), result = null) }, {}, {})
            }
        }
        compose.onNodeWithText("New chat").performClick()
        compose.onNodeWithText("What would you like to understand?").assertExists()
        compose.onNodeWithText("Who can use my microphone?").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Which apps have microphone access?")
    }
}
