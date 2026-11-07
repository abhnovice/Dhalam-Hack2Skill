package com.dhalam.ap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The protected content is not composed until Google sign-in is verified by the backend. */
@Composable
fun AuthenticatedContent(
    state: AskUiState,
    onSignIn: () -> Unit,
    content: @Composable () -> Unit
) {
    if (state.signedIn) {
        content()
        return
    }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Scaffold { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Dhalam", style = MaterialTheme.typography.headlineLarge)
                Text("Sign in to review your device’s permissions and security.",
                    modifier = Modifier.padding(vertical = 16.dp))
                if (state.signingIn) {
                    CircularProgressIndicator()
                    Text("Verifying sign-in…", Modifier.padding(vertical = 12.dp))
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
                Button(onClick = onSignIn, enabled = !state.signingIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}
