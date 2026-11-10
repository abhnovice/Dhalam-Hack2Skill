package com.dhalam.ap

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WhatsAppBotPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensOnlyAPublicChatDraftOnUserTap() {
        var opened = ""
        compose.setContent { MaterialTheme { WhatsAppBotPanel("+14155550123", { opened = it }) } }
        compose.runOnIdle { assertEquals("", opened) }
        compose.onNodeWithText("Open WhatsApp bot").performClick()
        compose.runOnIdle { assertEquals("https://wa.me/14155550123?text=CHECK%3A%20", opened) }
        compose.onNodeWithText("Save bot contact").assertIsEnabled()
        compose.onNodeWithText("Live call listening is unavailable.").assertExists()
    }

    @Test fun missingBotContactDoesNotOpenAnUnrelatedNumber() {
        compose.setContent { MaterialTheme { WhatsAppBotPanel("", {}) } }
        compose.onNodeWithText("Bot contact unavailable.").assertExists()
        compose.onNodeWithText("Open WhatsApp bot").assertIsNotEnabled()
        compose.onNodeWithText("Save bot contact").assertIsNotEnabled()
    }
}
