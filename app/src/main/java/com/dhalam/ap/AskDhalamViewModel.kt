package com.dhalam.ap

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dhalam.ap.data.SecurityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

/** Background work must not treat a previous launch as an authenticated session. */
internal object AppSessionAccess {
    @Volatile var signedIn = false
}

data class AskUiState(
    val question: String = "",
    val loading: Boolean = false,
    val configured: Boolean = false,
    val settingsReady: Boolean = true,
    val backendUrl: String = BuildConfig.ASK_BACKEND_URL,
    val signedIn: Boolean = false,
    val temporarySignIn: Boolean = false,
    val localSignIn: Boolean = false,
    val signingIn: Boolean = false,
    val accountLabel: String? = null,
    val error: String? = null,
    val result: AskResult? = null,
    val submittedQuestion: String? = null
)

class AskDhalamViewModel(application: Application) : AndroidViewModel(application) {
    private val backendUrl = runCatching {
        normalizeBackendUrl(BuildConfig.ASK_BACKEND_URL).also {
            require(BuildConfig.DEBUG || it.startsWith("https://"))
        }
    }.getOrNull()
    var state by mutableStateOf(AskUiState(configured = backendUrl != null &&
        BuildConfig.GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")))
        private set
    private var session: AskBackendSession? = null
    private var job: Job? = null

    init {
        // Remove the retired shared-token configuration; never read its contents.
        viewModelScope.launch(Dispatchers.IO) {
            File(application.noBackupFilesDir, "ask-backend.properties").delete()
            File(application.noBackupFilesDir, "ask-backend.properties.bak").delete()
        }
    }

    fun updateQuestion(value: String) { state = state.copy(question = value.take(2000)) }
    fun showError(message: String) { state = state.copy(error = message) }

    fun beginGoogleSignIn(): Boolean {
        if (state.signingIn || state.loading) return false
        if (!state.configured) {
            showError(if (BuildConfig.DEBUG) {
                if (!BuildConfig.GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")) {
                    "Google sign-in setup is incomplete: the developer must configure the OAuth Web client ID."
                } else {
                    "Google sign-in setup is incomplete: the developer must configure a valid backend URL."
                }
            } else {
                "Sign-in is temporarily unavailable. Please try again later."
            })
            return false
        }
        state = state.copy(signingIn = true, error = null)
        return true
    }

    fun signInFailed(message: String) {
        state = state.copy(signingIn = false, error = message)
    }

    fun completeGoogleSignIn(idToken: String, label: String) {
        job = viewModelScope.launch {
            try {
                val value = AskBackendSession(requireNotNull(backendUrl), idToken)
                AskBackend().verifySession(value)
                session = value
                AppSessionAccess.signedIn = true
                state = state.copy(signingIn = false, signedIn = true, temporarySignIn = false, localSignIn = false, accountLabel = label, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                signInFailed("Cannot connect to the service. Check your internet connection and try again.")
            } catch (error: AskSignInRequiredException) {
                signInFailed(error.message ?: "Sign in with Google again.")
            } catch (error: IllegalStateException) {
                signInFailed(error.message ?: "Sign-in is unavailable. Please try again.")
            } catch (_: Exception) {
                signInFailed("Could not verify your sign-in. Please try again.")
            }
        }
    }

    fun beginBiometricSignIn() {
        state = state.copy(signingIn = true, error = null)
    }

    /** Called only by Android's successful biometric callback; no biometric data is stored. */
    fun completeBiometricSignIn() {
        clear()
        session = null
        AppSessionAccess.signedIn = true
        state = state.copy(signedIn = true, localSignIn = true, temporarySignIn = false,
            accountLabel = "Local biometric session", error = null)
    }

    // TEMPORARY_LOGIN: remove this entire function after Google OAuth is working.
    // Unlocks local debug UI/scans only; it never creates a Google/backend credential.
    fun signInTemporarily(username: String, password: String): Boolean {
        if (!BuildConfig.DEBUG || !temporaryCredentialsMatch(username, password)) return false
        clear()
        session = null
        AppSessionAccess.signedIn = true
        state = state.copy(signedIn = true, temporarySignIn = true, localSignIn = false,
            accountLabel = "Temporary · abhyudayamarya", error = null)
        return true
    }

    private suspend fun <T> withGoogleSession(
        refreshIdentity: suspend () -> Pair<String, String>,
        action: suspend (AskBackendSession) -> T
    ): T {
        check(state.signedIn) { "Unlock Dhalam first." }
        val url = requireNotNull(backendUrl) { "The AI backend URL is not configured." }
        if (session != null) {
            return withRefreshedGoogleSession(session, refresh = {
                val (token, label) = refreshIdentity()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                check(state.signedIn) { "Session ended. Sign in again." }
                val refreshed = AskBackendSession(url, token)
                session = refreshed
                state = state.copy(temporarySignIn = false, localSignIn = false, accountLabel = label)
                refreshed
            }, onExpired = { session = null }, action = action)
        }
        return try {
            val (token, label) = refreshIdentity()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            check(state.signedIn) { "Session ended. Sign in again." }
            val refreshed = AskBackendSession(url, token)
            session = refreshed
            state = state.copy(temporarySignIn = false, localSignIn = false, accountLabel = label)
            action(refreshed)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: AskSignInRequiredException) {
            action(AskBackendSession(url))
        }
    }

    suspend fun analyzeTriage(payload: org.json.JSONObject, refreshIdentity: suspend () -> Pair<String, String>): org.json.JSONObject =
        withGoogleSession(refreshIdentity) { submitTriage(it.url, it.idToken.orEmpty(), payload) }

    fun send(snapshot: SecurityObservation?, refreshIdentity: suspend () -> Pair<String, String> = { throw AskSignInRequiredException() }) {
        if (state.loading || state.signingIn) return
        val question = state.question.trim()
        val currentSession = backendUrl
        when {
            !state.signedIn -> return showError("Unlock Dhalam before asking a question.")
            currentSession == null -> return showError("The developer must configure the AI backend URL.")
            question.isEmpty() -> return showError("Enter a question first.")
            snapshot == null -> return showError("Tap Scan now before asking about this device.")
        }
        state = state.copy(loading = true, error = null, result = null, submittedQuestion = question)
        job = viewModelScope.launch {
            try {
                val facts = withContext(Dispatchers.IO) { SecurityStore.repository(getApplication()).all() }
                val result = withGoogleSession(refreshIdentity) { AskBackend().ask(it, question, facts, requireNotNull(snapshot)) }
                state = state.copy(loading = false, result = result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: AskSignInRequiredException) {
                session = null
                AppSessionAccess.signedIn = false
                state = state.copy(question = "", loading = false, signedIn = false, temporarySignIn = false, localSignIn = false, accountLabel = null,
                    result = null, submittedQuestion = null,
                    error = "Your sign-in expired. Sign in with Google again.")
            } catch (_: SocketTimeoutException) {
                state = state.copy(loading = false, error = "The request timed out. Please try again.")
            } catch (failure: IOException) {
                state = state.copy(loading = false, error = connectionFailureMessage(state.backendUrl, failure))
            } catch (error: IllegalStateException) {
                state = state.copy(loading = false, error = error.message ?: "An answer is unavailable. Please retry.")
            } catch (_: Exception) {
                state = state.copy(loading = false, error = "The service returned an unsupported answer. Please retry.")
            }
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        state = state.copy(question = "", loading = false, signingIn = false, error = null,
            result = null, submittedQuestion = null)
    }

    fun signOut() {
        AppSessionAccess.signedIn = false
        clear()
        session = null
        state = state.copy(signedIn = false, temporarySignIn = false, localSignIn = false, accountLabel = null)
    }

    override fun onCleared() {
        AppSessionAccess.signedIn = false
        super.onCleared()
    }
}
