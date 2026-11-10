package com.dhalam.ap

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class DevicePairingUiTest {
    @get:Rule val compose = createAndroidComposeRule<DeviceReviewCompanionActivity>()

    @Test fun tvExposesPairingAndReturnInstructions() {
        compose.onNodeWithText("Pair a device").assertIsDisplayed()
        compose.onNodeWithText("Bluetooth settings").assertIsDisplayed()
        compose.onNodeWithText("Pair a device, then show a code. Settings: press Back to return.").assertIsDisplayed()
        compose.onNodeWithText("Show pairing code").assertIsDisplayed()
    }
}
