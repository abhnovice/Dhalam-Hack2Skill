package com.dhalam.ap

import com.dhalam.ap.data.SecurityStore
import kotlinx.coroutines.CancellationException
import android.Manifest
import android.app.NotificationManager
import android.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {
    private lateinit var biometricPrompt: BiometricPrompt
    private val askViewModel by lazy { ViewModelProvider(this)[AskDhalamViewModel::class.java] }
    private val credentialManager by lazy { CredentialManager.create(this) }
    private var pendingLocalAction: (() -> Unit)? = null
    private val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    private val localNetworkRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingLocalAction
        pendingLocalAction = null
        if (granted) action?.invoke()
        else askViewModel.showError("Local network access was denied. Allow Nearby devices in App settings, then retry.")
    }
    private val captureStatus = mutableStateOf("Unavailable")
    private val captureObservation = mutableStateOf(CaptureObservation())
    private val capture by lazy { CaptureMonitor(this) {
        captureObservation.value = it
        captureStatus.value = it.recording
    } }
    override fun onStart() { super.onStart(); capture.start() }
    override fun onStop() { capture.stop(); super.onStop() }

    private val observation = mutableStateOf<SecurityObservation?>(null)
    private val scanning = mutableStateOf(false)
    private var scanJob: Job? = null
    private var credentialJob: Job? = null
    private val scanError = mutableStateOf<String?>(null)
    private val notificationsEnabled = mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshNotificationStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    askViewModel.completeBiometricSignIn()
                }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    askViewModel.signInFailed(message.toString())
                }
                override fun onAuthenticationFailed() {
                    // Android keeps the prompt open for another attempt; never unlock here.
                }
            })

        setContent {
            val authState = askViewModel.state
            LaunchedEffect(authState.signedIn) {
                if (authState.signedIn) {
                    startPeriodicScans()
                    scan()
                } else {
                    scanJob?.cancel()
                    scanning.value = false
                    observation.value = null
                    scanError.value = null
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("dhalam-security-scan")
                    getSystemService(NotificationManager::class.java)?.cancel(1001)
                }
            }
            AuthenticatedContent(
                authState,
                onSignIn = ::signInWithGoogle,
                // TEMPORARY_LOGIN: remove this callback after Google OAuth works.
                onTemporarySignIn = askViewModel::signInTemporarily,
                onBiometricSignIn = ::signInWithBiometrics
            ) {
                SecurityDashboard(
                observation = observation.value,
                scanning = scanning.value,
                scanError = scanError.value,
                notificationsEnabled = notificationsEnabled.value,
                onScan = ::scan,
                onEnableAlerts = ::enableAlerts,
                onOpenAccessibility = {
                    openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                captureStatus = captureStatus.value,
                captureObservation = captureObservation.value,
                onOpenDeveloperOptions = {
                    openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                },
                askState = askViewModel.state,
                onQuestionChange = askViewModel::updateQuestion,
                onAsk = ::sendAskQuestion,
                onClearAsk = askViewModel::clear,
                onSignIn = ::signInWithGoogle,
                onSignOut = ::signOutGoogle,
                onAccessReport = { snapshot -> askViewModel.accessibilityReport(snapshot, ::refreshGoogleIdentity) },
                onAnalyze = { payload -> askViewModel.analyzeTriage(payload, ::refreshGoogleIdentity) }
                )
            }
        }
    }

    private fun startPeriodicScans() {
        val request = PeriodicWorkRequestBuilder<SecurityScanWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "dhalam-security-scan",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationStatus()
        scan()
    }

    private fun scan() {
        if (!askViewModel.state.signedIn || scanning.value) return
        scanning.value = true
        scanError.value = null

        val captured = captureStatus.value
        val notifications = notificationsEnabled.value
        scanJob = lifecycleScope.launch {
            try {
                observation.value = withContext(Dispatchers.IO) {
                    collectSecurityObservation(applicationContext).also { snapshot ->
                        coroutineContext.ensureActive()
                        if (!AppSessionAccess.signedIn) throw CancellationException("Signed out")
                        val repository = SecurityStore.repository(applicationContext)
                        repository.saveObservation(snapshot)
                        SecurityJournalStore.record(applicationContext, snapshot, notifications, captured)
                        Log.i("DhalamStorage", "Foreground scan saved. Stored facts: ${repository.count()}")
                    }
                }
            } catch (error: SecurityException) {
                Log.w("DhalamScan", "Android blocked a scan read.", error)
                scanError.value = "Android blocked a scan read. Try Scan now again."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("DhalamStorage", "Could not save scan.", error)
                scanError.value = "Could not complete and save the scan. Try Scan now again."
            } finally {
                scanning.value = false
            }
        }
    }

    private fun sendAskQuestion() {
        if (scanning.value) {
            askViewModel.showError("Wait for the scan to finish, then send again.")
            return
        }
        if (!askViewModel.state.signedIn) {
            askViewModel.send(observation.value, ::refreshGoogleIdentity)
            return
        }
        withLocalNetworkAccess { askViewModel.send(observation.value, ::refreshGoogleIdentity) }
    }

    private fun signInWithBiometrics() {
        if (askViewModel.state.signingIn || askViewModel.state.loading) return
        val status = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            askViewModel.showError(when (status) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    "Add a fingerprint or supported face in Android Settings, then try again."
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    "This device has no supported biometric sensor. You can use Google sign-in."
                else -> "Strong biometric authentication is unavailable right now. Try again or use Google."
            })
            return
        }
        askViewModel.beginBiometricSignIn()
        biometricPrompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(tr("Unlock Dhalam"))
            .setSubtitle(tr("Use your device biometric to continue locally"))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(tr("Cancel"))
            .build())
    }

    private fun signInWithGoogle() {
        if (!askViewModel.state.configured) {
            askViewModel.beginGoogleSignIn()
            return
        }
        withLocalNetworkAccess {
            if (!askViewModel.beginGoogleSignIn()) return@withLocalNetworkAccess
            credentialJob = lifecycleScope.launch {
                try {
                    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
                    val response = credentialManager.getCredential(
                        context = MutableContextWrapper(this@MainActivity),
                        request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                    )
                    val credential = response.credential as? CustomCredential
                    check(credential?.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                    val google = GoogleIdTokenCredential.createFrom(requireNotNull(credential).data)
                    askViewModel.completeGoogleSignIn(google.idToken, google.displayName ?: google.id)
                } catch (_: GetCredentialCancellationException) {
                    askViewModel.signInFailed(if (BuildConfig.DEBUG)
                        "Google sign-in did not complete. If you selected an account, check this build’s Android OAuth client and SHA-1."
                    else "Google sign-in did not complete. Please try again.")
                } catch (_: NoCredentialException) {
                    askViewModel.signInFailed("No Google account is available. Add an account to this device and try again.")
                } catch (_: GetCredentialException) {
                    askViewModel.signInFailed(if (BuildConfig.DEBUG)
                        "Google rejected this OAuth setup. Check that the server client ID is a Web application client and the Android client matches this app’s SHA-1."
                    else "Google sign-in could not complete. Please try again.")
                } catch (error: CancellationException) {
                    askViewModel.signInFailed("Sign-in was interrupted. Please try again.")
                    throw error
                } catch (_: Exception) {
                    askViewModel.signInFailed("Google sign-in returned an unsupported response. Please try again.")
                }
            }
        }
    }

    private suspend fun refreshGoogleIdentity(): Pair<String, String> {
        try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
            val response = credentialManager.getCredential(
                context = this@MainActivity,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
            val credential = response.credential as? CustomCredential
            if (credential?.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                return google.idToken to (google.displayName ?: google.id)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Auto-select not available; try interactive sign in
        }

        try {
            val interactiveOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val response = credentialManager.getCredential(
                context = this@MainActivity,
                request = GetCredentialRequest.Builder().addCredentialOption(interactiveOption).build()
            )
            val credential = response.credential as? CustomCredential
            if (credential?.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                return google.idToken to (google.displayName ?: google.id)
            }
            throw AskSignInRequiredException()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            throw AskSignInRequiredException()
        }
    }

    private fun signOutGoogle() {
        credentialJob?.cancel()
        credentialJob = null
        pendingLocalAction = null
        biometricPrompt.cancelAuthentication()
        askViewModel.signOut()
        lifecycleScope.launch {
            try { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Local credentials are already cleared. */ }
        }
    }

    private fun withLocalNetworkAccess(action: () -> Unit) {
        val host = Uri.parse(askViewModel.state.backendUrl).host.orEmpty()
        val localHost = host == "localhost" || host == "127.0.0.1" ||
            host.startsWith("10.") || host.startsWith("192.168.") || host.endsWith(".local") ||
            (host.startsWith("172.") && host.split('.').getOrNull(1)?.toIntOrNull() in 16..31)
        if (BuildConfig.DEBUG && localHost &&
            Build.VERSION.SDK_INT >= 37 && checkSelfPermission(localNetworkPermission) != PackageManager.PERMISSION_GRANTED) {
            pendingLocalAction = action
            val prefs = getSharedPreferences("dhalam_permissions", MODE_PRIVATE)
            if (shouldShowRequestPermissionRationale(localNetworkPermission)) {
                AlertDialog.Builder(this).setTitle(tr("Connect to your demo backend"))
                    .setMessage(tr("Ask Dhalam needs local network access to reach the backend running on your computer."))
                    .setPositiveButton(tr("Continue")) { _, _ -> localNetworkRequest.launch(localNetworkPermission) }
                    .setNegativeButton(tr("Cancel"), null).show()
            } else if (prefs.getBoolean("asked_for_local_network", false)) {
                AlertDialog.Builder(this).setTitle(tr("Allow local network access"))
                    .setMessage(tr("Enable Nearby devices for Dhalam in App settings to reach your local backend."))
                    .setPositiveButton(tr("App settings")) { _, _ ->
                        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                    }.setNegativeButton(tr("Cancel"), null).show()
            } else {
                prefs.edit().putBoolean("asked_for_local_network", true).apply()
                localNetworkRequest.launch(localNetworkPermission)
            }
            return
        }
        action()
    }

    private fun refreshNotificationStatus() {
        notificationsEnabled.value = getSystemService(NotificationManager::class.java)
            ?.areNotificationsEnabled() == true
    }

    private fun enableAlerts() {
        if (!askViewModel.state.signedIn) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("dhalam_permissions", MODE_PRIVATE)
            if (!prefs.getBoolean("asked_for_notifications", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                prefs.edit().putBoolean("asked_for_notifications", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        }
        openSettings(intent)
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
