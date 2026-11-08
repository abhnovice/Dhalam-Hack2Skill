package com.dhalam.ap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SignInBrandFont = com.dhalam.ap.ui.theme.BrandFont

/** Protected content requires Google sign-in, local biometric unlock, or debug-only temporary login. */
@Composable
fun AuthenticatedContent(
    state: AskUiState,
    onSignIn: () -> Unit,
    onTemporarySignIn: (String, String) -> Boolean = { _, _ -> false },
    onBiometricSignIn: () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (state.signedIn) {
        content()
        return
    }
    // TEMPORARY_LOGIN: remove this dialog state and UI block after OAuth is working.
    var showTemporarySignIn by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) com.dhalam.ap.ui.theme.DarkColors else com.dhalam.ap.ui.theme.LightColors,
        typography = com.dhalam.ap.ui.theme.Typography
    ) {
        if (BuildConfig.DEBUG && showTemporarySignIn) {
            TemporarySignInDialog(
                onDismiss = { showTemporarySignIn = false },
                onSubmit = onTemporarySignIn
            )
        }
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.widthIn(max = 360.dp).verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.dhalam_logo_transparent),
                            contentDescription = "Dhalam logo",
                            modifier = Modifier.size(156.dp)
                        )
                        Text(
                            "Dhalam",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = SignInBrandFont,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            "Sign in to review your device’s permissions and security.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }

                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth()
                        .padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.signingIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Verifying sign-in…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onBiometricSignIn,
                        enabled = !state.signingIn,
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp)
                    ) { Text("Unlock with biometrics") }
                    Text(
                        "Choose biometrics or Google. Biometric data stays on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = onSignIn,
                        enabled = !state.signingIn,
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, Color(0xFF747775)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F),
                            disabledContainerColor = Color.White.copy(alpha = 0.7f),
                            disabledContentColor = Color(0xFF747775)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.google_g),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Sign in with Google",
                            fontFamily = com.dhalam.ap.ui.theme.BrandFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    // TEMPORARY_LOGIN: future phone sign-in occupies this position.
                    if (BuildConfig.DEBUG) {
                        TextButton(
                            onClick = { showTemporarySignIn = true },
                            enabled = !state.signingIn,
                            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                        ) {
                            Text("Temporary developer sign-in")
                        }
                    }
                }
            }
        }
    }
}


// TEMPORARY_LOGIN START — remove both functions when Google OAuth is working.
/** Local development credentials only. Release builds always reject this login. */
internal fun temporaryCredentialsMatch(username: String, password: String): Boolean =
    BuildConfig.DEBUG && username == "abhyudayamarya" && password == "dummydhalam"

/** Temporary UI in the position reserved for future phone sign-in. */
@Composable
internal fun TemporarySignInDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Boolean
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submit = {
        if (onSubmit(username, password)) {
            username = ""
            password = ""
            onDismiss()
        } else {
            error = "Incorrect username or password."
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporary developer sign-in") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Debug-only access for local dashboard testing.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = username.isNotBlank() && password.isNotEmpty()) {
                Text("Sign in")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
// TEMPORARY_LOGIN END
