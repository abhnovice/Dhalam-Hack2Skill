package com.dhalam.ap

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dhalam.ap.data.SecurityFact
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AskDhalamTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logoutIsAvailableOnTheDefaultScamTab() {
        val signedIn = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            AuthenticatedContent(AskUiState(signedIn = signedIn.value), {}) {
                SecurityDashboard(null, false, null, false, {}, {}, {}, {},
                    AskUiState(signedIn = true), {}, {}, {}, {}, { signedIn.value = false })
            }
        }
        compose.onNodeWithText("Log out").performClick()
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
        compose.onNodeWithText("Check").assertDoesNotExist()
    }

    @Test fun chatLinkInspectionStaysLocal() {
        var submissions = 0
        compose.setContent { MaterialTheme { ScamTriagePanel(
            androidx.compose.ui.Modifier.fillMaxSize(),
            analyze = { submissions++; org.json.JSONObject() }
        ) } }
        compose.onNode(hasSetTextAction()).performTextInput("https://trusted.example@evil.example/login")
        compose.onNodeWithText("Inspect link locally").performClick()
        compose.onNodeWithText("evil.example").assertExists()
        compose.onNodeWithText("Path: /login").assertExists()
        assertEquals(0, submissions)
        compose.onNodeWithText("Close").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("No link here")
        compose.onNodeWithText("Inspect link locally").assertDoesNotExist()
    }

    @Test fun scamChatRequiresExplicitConsentAndCanCancel() {
        compose.setContent { MaterialTheme { ScamTriagePanel(androidx.compose.ui.Modifier.fillMaxSize()) } }
        compose.onNodeWithText("Check").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Someone is asking for money")
        compose.onNodeWithText("Check").performClick()
        compose.onNodeWithText("Send for an AI check?").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Send for an AI check?").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("Someone is asking for money")
    }

    @Test fun referenceGuidanceUsesReturnedSourcesAndDisclosesFallback() {
        assertTrue(referenceNotes(org.json.JSONObject()).contains("not backed by retrieved sources"))
        val result = org.json.JSONObject("""{"sources":[{"publisher":"RBI","title":"Synthetic reference","url":"https://rbi.org.in/reference"}]}""")
        assertTrue(referenceNotes(result).contains("https://rbi.org.in/reference"))
        assertTrue(referenceNotes(result).contains("not proof"))
    }

    private val fact = SecurityFact(7, "camera", "permission", "demo.app",
        "Demo requested android.permission.CAMERA", "granted", 10)
    private fun response(id: String = "fact:7", score: String = "null") = """
        {"answer":"Camera permission is granted.","findings":[
          {"title":"Camera access","severity":"low","explanation":"Review whether needed.","evidenceIds":["$id"]}
        ],"recommendations":["Review permissions."],"limitations":["Only selected facts."],
        "exposureScore":$score,"scoreExplanation":null}
    """.trimIndent()

    @Test fun parsesCitationsBackToOriginalFacts() {
        val result = parseAskResult(response(), listOf(fact), 10)
        assertEquals(fact, result.findings.single().sources.single())
        assertEquals(10L, result.observedAt)
    }

    @Test fun ignoresBlankOptionalTextWithoutLosingCitations() {
        val text = response().replace("[\"Review permissions.\"]", "[\"\", \"Review permissions.\", \"  \"]")
            .replace("[\"Only selected facts.\"]", "[\"\"]")
        val result = parseAskResult(text, listOf(fact), 10)
        assertEquals(listOf("Review permissions."), result.recommendations)
        assertTrue(result.limitations.isEmpty())
        assertEquals(fact, result.findings.single().sources.single())
    }

    @Test fun rejectsInventedCitationsAndScores() {
        assertThrows(IllegalStateException::class.java) { parseAskResult(response("invented"), listOf(fact), 10) }
        assertThrows(IllegalStateException::class.java) { parseAskResult(response(score = "90"), listOf(fact), 10) }
    }

    @Test fun inputSendsThroughButtonAndKeyboardAndCanClear() {
        var sends = 0
        var clears = 0
        compose.setContent {
            MaterialTheme {
                AskDhalamInput(AskUiState(question = "Camera?", configured = true, settingsReady = true, signedIn = true),
                    false, {}, { sends++ }, { clears++ }, {}, {})
            }
        }
        compose.onNodeWithText("Send").performClick()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(2, sends) }
        compose.onNodeWithText("Clear").performClick()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test fun blankInputCannotSend() {
        compose.setContent {
            MaterialTheme { AskDhalamInput(AskUiState(settingsReady = true), false, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test fun signedOutUserSeesGoogleSignInInsteadOfTokenSettings() {
        var signIns = 0
        compose.setContent {
            MaterialTheme {
                AskDhalamInput(AskUiState(question = "Camera?"), false, {}, {}, {}, { signIns++ }, {})
            }
        }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.runOnIdle { assertEquals(1, signIns) }
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNodeWithText("Configure backend").assertDoesNotExist()
    }

    @Test fun signedOutGateNeverComposesProtectedContent() {
        var protectedCompositions = 0
        var signIns = 0
        compose.setContent {
            AuthenticatedContent(AskUiState(), { signIns++ }) {
                protectedCompositions++
                androidx.compose.material3.Text("Protected dashboard")
            }
        }
        compose.onNodeWithText("Protected dashboard").assertDoesNotExist()
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.runOnIdle {
            assertEquals(0, protectedCompositions)
            assertEquals(1, signIns)
        }
    }

    @Test fun signingOutRemovesProtectedContent() {
        val signedIn = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            AuthenticatedContent(AskUiState(signedIn = signedIn.value), {}) {
                androidx.compose.material3.Text("Protected dashboard")
            }
        }
        compose.onNodeWithText("Protected dashboard").assertIsDisplayed()
        compose.runOnIdle { signedIn.value = false }
        compose.onNodeWithText("Protected dashboard").assertDoesNotExist()
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    @Test fun errorsShowRetry() {
        var retries = 0
        compose.setContent {
            MaterialTheme { AskAnswerPanel(AskUiState(error = "Cannot reach backend."), 10, { retries++ }) }
        }
        compose.onNodeWithText("Cannot reach backend.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
