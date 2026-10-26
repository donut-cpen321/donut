package com.example.cpen321application.helpers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
            } catch (intentErr: Exception) {
                Log.e("Auth", "Failed to open Add Account screen", intentErr)
            }
        }
        null
    } catch (e: GetCredentialCancellationException) {
        Log.i("Auth", "User cancelled Google Sign-In prompt")
        null
    } catch (e: GetCredentialException) {
        Log.e("Auth", "Google Sign-In failed: ${e.message}", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
        null
    } catch (e: Exception) {
        Log.e("Auth", "Unexpected error during Google Sign-In: ${e.message}", e)
        null
    }
}


/**
 * Signs out a user from the app
 * @param context
 */
suspend fun signOut(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
        Log.e("Auth", "Failed to clear credentials", e)
    }
}
/**
 * Logs in a user to the backend
 * @param apiBaseUrl The base URL of the backend
 * @param idToken The Google authentication token
 * @return User object or null if login failed
 */
suspend fun loginWithBackend(apiBaseUrl: String, idToken: String): User? = withContext(Dispatchers.IO) {
    val loginUrl = "${apiBaseUrl.trimEnd('/')}/user/login"
    try {
        val connection = (URL(loginUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val jsonBody = JSONObject().apply {
            put("idToken", idToken)
        }.toString()

        connection.outputStream.use { it.write(jsonBody.toByteArray()) }

        when (val code = connection.responseCode) {
            HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    val json = JSONObject(body)
                    User(
                        username = json.getString("username"),
                        accessToken = json.getString("accessToken"),
                        refreshToken = json.getString("refreshToken")
                    )
                } catch (e: Exception) {
                    null
                }
            }
            else -> {
                Log.e("Auth", "Backend login failed: HTTP $code")
                null
            }
        }
    } catch (e: Exception) {
        Log.e("Auth", "Backend unreachable", e)
        null
    }
}