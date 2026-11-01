package com.example.cpen321application.core.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class User(
    val username: String,
    val accessToken: String,
    val refreshToken: String
)

/**
 * Launches google sign-in
 * @param context The activity context
 * @param serverClientId The server client ID obtained from the Google Cloud Console
 * @return Google authentication token or null if sign-in failed
 */
suspend fun signInWithGoogle(context: Context, serverClientId: String): String? {
    if (serverClientId.isBlank()) {
        Log.e("Auth", "GOOGLE_CLIENT_ID is empty! Check local.properties")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error: GOOGLE_CLIENT_ID is missing", Toast.LENGTH_LONG).show()
        }
        return null
    }

    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(serverClientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential

        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            googleIdTokenCredential.idToken
        } else {
            Log.e("Auth", "Unexpected credential type: ${credential.type}")
            null
        }
    } catch (e: NoCredentialException) {
        Log.e("Auth", "No Google account found on device: ${e.message}", e)
        addGoogleAccountToDevice(context)
        null
    } catch (_: GetCredentialCancellationException) {
        Log.i("Auth", "User cancelled Google Sign-In prompt")
        null
    } catch (e: GetCredentialException) {
        Log.e("Auth", "Google Sign-In failed: ${e.message}", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
        null
    }
}

suspend fun addGoogleAccountToDevice(context: Context) {
    withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            "No Google account on device. Opening Settings to add one...",
            Toast.LENGTH_LONG
        ).show()
        try {
            val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("Auth", "Add Account screen not found on device", e)
        } catch (e: SecurityException) {
            Log.e("Auth", "Permission denied to open Add Account screen", e)
        }
    }
}


/**
 * Signs out a user from the app
 * @param context
 */
suspend fun signOut(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: ClearCredentialException) {
        Log.e("Auth", "Failed to clear credentials", e)
    }
}
