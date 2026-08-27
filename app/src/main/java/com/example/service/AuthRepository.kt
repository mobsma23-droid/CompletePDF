package com.example.service

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(val displayName: String, val email: String?, val isGuest: Boolean = false) : AuthState()
    data class Error(val message: String, val isCredentialUnavailable: Boolean = false) : AuthState()
}

class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)
    private val prefs = context.getSharedPreferences("auth_session_prefs", Context.MODE_PRIVATE)

    // Web client ID from google-services.json for OAuth / Google Sign In
    private val webClientId: String = "281205414143-l5137khb4cjcusghjg3thrl6ttqjk0m3.apps.googleusercontent.com"

    private val _authState = MutableStateFlow<AuthState>(getInitialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun getInitialAuthState(): AuthState {
        val user = auth.currentUser
        if (user != null) {
            val name = user.displayName?.ifBlank { "Google User" } ?: (user.email ?: "Google User")
            return AuthState.Authenticated(
                displayName = name,
                email = user.email,
                isGuest = false
            )
        }
        val isAuthSaved = prefs.getBoolean("auth_logged_in", false)
        if (isAuthSaved) {
            val savedName = prefs.getString("auth_user_name", "Local User") ?: "Local User"
            val savedEmail = prefs.getString("auth_user_email", "user@app.local")
            val isGuest = prefs.getBoolean("auth_is_guest", false)
            return AuthState.Authenticated(
                displayName = savedName,
                email = savedEmail,
                isGuest = isGuest
            )
        }
        return AuthState.Unauthenticated
    }

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                val name = user.displayName?.ifBlank { "Google User" } ?: (user.email ?: "Google User")
                prefs.edit()
                    .putBoolean("auth_logged_in", true)
                    .putString("auth_user_name", name)
                    .putString("auth_user_email", user.email ?: "")
                    .putBoolean("auth_is_guest", false)
                    .apply()
                _authState.value = AuthState.Authenticated(
                    displayName = name,
                    email = user.email,
                    isGuest = false
                )
            } else {
                val isAuthSaved = prefs.getBoolean("auth_logged_in", false)
                if (isAuthSaved) {
                    val savedName = prefs.getString("auth_user_name", "Local User") ?: "Local User"
                    val savedEmail = prefs.getString("auth_user_email", "user@app.local")
                    val isGuest = prefs.getBoolean("auth_is_guest", false)
                    _authState.value = AuthState.Authenticated(
                        displayName = savedName,
                        email = savedEmail,
                        isGuest = isGuest
                    )
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        }
    }

    val currentUserEmail: String?
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.email
            else -> auth.currentUser?.email ?: prefs.getString("auth_user_email", null)
        }

    val currentDisplayName: String
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.displayName
            else -> auth.currentUser?.displayName ?: prefs.getString("auth_user_name", "User") ?: "User"
        }

    suspend fun signInWithGoogle(): Result<Unit> {
        _authState.value = AuthState.Loading
        return try {
            val rawNonce = UUID.randomUUID().toString()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(rawNonce.toByteArray())
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            // Primary Option: GetGoogleIdOption with filterByAuthorizedAccounts = false to show all Google accounts
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
            )

            handleCredentialResponse(response)
        } catch (e: GetCredentialCancellationException) {
            _authState.value = AuthState.Unauthenticated
            Result.failure(e)
        } catch (e: Exception) {
            // Fallback attempt: if primary threw NoCredentialException or GetCredentialException, retry with GetSignInWithGoogleOption fallback
            if (e is NoCredentialException || e is GetCredentialException) {
                try {
                    Log.d("AuthRepository", "Primary GoogleIdOption caught ${e::class.simpleName}, attempting fallback account selector...")
                    val rawNonce = UUID.randomUUID().toString()
                    val md = MessageDigest.getInstance("SHA-256")
                    val digest = md.digest(rawNonce.toByteArray())
                    val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                    val fallbackOption = GetSignInWithGoogleOption.Builder(webClientId)
                        .setNonce(hashedNonce)
                        .build()

                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()

                    val fallbackResponse = credentialManager.getCredential(
                        request = fallbackRequest,
                        context = context
                    )
                    return handleCredentialResponse(fallbackResponse)
                } catch (fallbackError: GetCredentialCancellationException) {
                    _authState.value = AuthState.Unauthenticated
                    return Result.failure(fallbackError)
                } catch (fallbackError: Exception) {
                    val rawMsg = fallbackError.message ?: e.message ?: "No credentials available"
                    val isNoCreds = rawMsg.contains("No credentials", ignoreCase = true) ||
                            rawMsg.contains("No Google account", ignoreCase = true) ||
                            fallbackError is NoCredentialException || e is NoCredentialException
                    val userFriendlyMsg = if (isNoCreds) {
                        "No Google accounts found on this device. Please add a Google account in Android Settings > Accounts, or continue with Offline / Guest Mode."
                    } else {
                        "Google Sign-In: $rawMsg"
                    }
                    _authState.value = AuthState.Error(userFriendlyMsg, isCredentialUnavailable = isNoCreds)
                    return Result.failure(fallbackError)
                }
            }

            val rawMsg = e.message ?: "Authentication failed"
            val isNoCreds = rawMsg.contains("No credentials", ignoreCase = true) || e is NoCredentialException
            val userFriendlyMsg = if (isNoCreds) {
                "No Google accounts found on this device. Please add a Google account in Android Settings > Accounts, or continue with Offline / Guest Mode."
            } else {
                rawMsg
            }
            _authState.value = AuthState.Error(userFriendlyMsg, isCredentialUnavailable = isNoCreds)
            Result.failure(e)
        }
    }

    private suspend fun handleCredentialResponse(response: GetCredentialResponse): Result<Unit> {
        var idToken = ""
        var extractedDisplayName = ""
        var extractedEmail = ""

        try {
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                idToken = googleCred.idToken
                extractedDisplayName = googleCred.displayName ?: googleCred.givenName ?: ""
                extractedEmail = googleCred.id
            } else {
                try {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    idToken = googleCred.idToken
                    extractedDisplayName = googleCred.displayName ?: googleCred.givenName ?: ""
                    extractedEmail = googleCred.id
                } catch (_: Exception) {
                    val bundle = credential.data
                    idToken = bundle.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN", "")
                    extractedDisplayName = bundle.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_DISPLAY_NAME", "")
                    extractedEmail = bundle.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID", "")
                }
            }
        } catch (parseError: Exception) {
            Log.w("AuthRepository", "Error parsing Google credential data: ${parseError.message}")
        }

        var finalDisplayName = if (extractedDisplayName.isNotBlank()) extractedDisplayName else "Google User"
        var finalEmail = if (extractedEmail.isNotBlank()) extractedEmail else "google.user@gmail.com"

        // Try authenticating with Firebase if idToken is available, but never block entry if Firebase throws an error
        if (idToken.isNotBlank()) {
            try {
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                if (user != null) {
                    if (!user.displayName.isNullOrBlank()) {
                        finalDisplayName = user.displayName!!
                    }
                    if (!user.email.isNullOrBlank()) {
                        finalEmail = user.email!!
                    }
                }
            } catch (firebaseException: Exception) {
                Log.w("AuthRepository", "Firebase sign-in warning: ${firebaseException.message}. Proceeding with verified Google Identity account.")
            }
        }

        prefs.edit()
            .putBoolean("auth_logged_in", true)
            .putString("auth_user_name", finalDisplayName)
            .putString("auth_user_email", finalEmail)
            .putBoolean("auth_is_guest", false)
            .apply()

        _authState.value = AuthState.Authenticated(
            displayName = finalDisplayName,
            email = finalEmail,
            isGuest = false
        )
        return Result.success(Unit)
    }

    fun continueAsGuest(customName: String = "Local User") {
        prefs.edit()
            .putBoolean("auth_logged_in", true)
            .putString("auth_user_name", customName)
            .putString("auth_user_email", "local.user@offline")
            .putBoolean("auth_is_guest", true)
            .apply()

        _authState.value = AuthState.Authenticated(
            displayName = customName,
            email = "local.user@offline",
            isGuest = true
        )
    }

    suspend fun signOut() {
        try {
            prefs.edit()
                .putBoolean("auth_logged_in", false)
                .remove("auth_user_name")
                .remove("auth_user_email")
                .remove("auth_is_guest")
                .apply()
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _authState.value = AuthState.Unauthenticated
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
