package com.dhalam.ap

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dhalam.ap.data.SecurityFact
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AskDhalamTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithText("Send", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(2, sends) }
        compose.onNodeWithText("Clear").performClick()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test fun blankInputCannotSend() {
        compose.setContent {
            MaterialTheme { AskDhalamInput(AskUiState(settingsReady = true), false, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Send", useUnmergedTree = true).assertIsNotEnabled()
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
        compose.onNodeWithText("Send", useUnmergedTree = true).assertIsNotEnabled()
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
