package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.GeminiService
import com.example.data.repository.FirestoreRepository
import com.example.security.configureAppCheck
import com.example.ui.MainNavigation
import com.example.ui.auth.SignInScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "MainActivity"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global uncaught exception logger to catch and inspect any unexpected errors
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // STEP 1: Mandatory App Check configuration with launch intent before UI or Firebase operations
        try {
            configureAppCheck(this, intent)
        } catch (e: Throwable) {
            Log.w(TAG, "App Check initialization warning", e)
        }

        // STEP 2: Edge-to-edge full bleed layout
        enableEdgeToEdge()

        // STEP 3: Compose Root with Auth Gating
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }

    @Composable
    private fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
        val authFlow = remember(auth) { auth.authStateFlow() }
        val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = auth.currentUser)
        val user = currentUser

        if (user == null) {
            // Unauthenticated state: display Google Sign-In gate
            SignInScreen(
                onSignInSuccess = {
                    // Auth state flow automatically recomposes with authenticated session
                }
            )
        } else {
            // Authenticated session: pass authenticated uid to repositories and UI
            val repository = remember(user.uid) { FirestoreRepository(this@MainActivity) }
            val geminiService = remember { GeminiService() }

            MainNavigation(
                currentUserId = user.uid,
                repository = repository,
                geminiService = geminiService
            )
        }
    }
}
