package com.example.cpen321application.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

/**
 * Sends a JSON request to the backend
 * @param url full URL of the endpoint
 * @param method HTTP method, e.g. "GET" or "POST"
 * @param body JSON body to send, null for none
 * @param accessToken sent as `Authorization: Bearer <accessToken>` when not null
 * @return the status code and response body, for error codes too
 * @throws IOException if the backend is unreachable
 */
suspend fun httpRequest(
    url: String,
    method: String,
    body: JSONObject? = null,
    accessToken: String? = null
): HttpResponse = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 5_000
        readTimeout = 5_000
        setRequestProperty("Content-Type", "application/json")
        if (accessToken != null) {
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
    }

    try {
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
        }

        val code = connection.responseCode
        // Error responses (4xx/5xx) are read from errorStream, which is null when the body is empty
        val stream = if (code < 400) connection.inputStream else connection.errorStream
        HttpResponse(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
    } finally {
        connection.disconnect()
    }
}

/**
 * Owns the signed-in user and their tokens.
 * Sends authenticated requests, refreshes the access token when it expires and logs out.
 * @param apiBaseUrl The base URL of the backend
 * @param initialUser the user saved from the last session, null if signed out
 * @param onUserChanged called with the new user after sign-in or a refresh, null after sign-out
 */
class AuthSession(
    apiBaseUrl: String,
    initialUser: User?,
    private val onUserChanged: (User?) -> Unit
) {
    private val baseUrl = apiBaseUrl.trimEnd('/')

    // Refresh tokens only work once, so two refreshes at the same time would sign the user out
    private val lock = Mutex()

    @Volatile
    var user: User? = initialUser
        private set

    /**
     * Starts a session with the user returned by loginWithBackend
     */
    fun signIn(user: User) = setUser(user)

    /**
     * Sends a request with the access token. If it expired, refreshes it once and retries.
     * @param method HTTP method, e.g. "GET" or "POST"
     * @param path endpoint path, e.g. "/games"
     * @param body JSON body to send, null for none
     * @return the backend's response, null if no user is signed in
     * @throws IOException if the backend is unreachable
     */
    suspend fun request(method: String, path: String, body: JSONObject? = null): HttpResponse? {
        val sentAs = user ?: return null
        val response = httpRequest("$baseUrl$path", method, body, sentAs.accessToken)
        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return response
        }

        val refreshedUser = refreshTokens(sentAs) ?: return response
        return httpRequest("$baseUrl$path", method, body, refreshedUser.accessToken)
    }

    /**
     * Signs out on this device and deletes the refresh token on the backend.
     * The user is signed out even if the backend is unreachable.
     */
    suspend fun logout() {
        val signedOut = lock.withLock {
            val current = user ?: return
            setUser(null)
            current
        }

        try {
            httpRequest("$baseUrl/user/logout", "POST", JSONObject().put("refreshToken", signedOut.refreshToken))
        } catch (e: IOException) {
            // Already signed out here; the refresh token expires on its own
        }
    }

    /**
     * Trades the refresh token for new tokens
     * @param staleUser the user whose access token got a 401
     * @return the user with new tokens, null if the user has to sign in again or the refresh failed
     */
    private suspend fun refreshTokens(staleUser: User): User? = lock.withLock {
        val current = user
        // Another request already refreshed, or the user signed out, while this one waited
        if (current != staleUser) {
            return@withLock current
        }

        val response = httpRequest(
            "$baseUrl/user/refresh",
            "POST",
            JSONObject().put("refreshToken", staleUser.refreshToken)
        )

        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                val json = JSONObject(response.body)
                val refreshedUser = staleUser.copy(
                    accessToken = json.getString("accessToken"),
                    refreshToken = json.getString("refreshToken")
                )
                setUser(refreshedUser)
                refreshedUser
            }
            // Refresh token used, expired or logged out: the user has to sign in with Google again
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                setUser(null)
                null
            }
            // Server error: keep the session, the next request tries again
            else -> null
        }
    }

    private fun setUser(newUser: User?) {
        user = newUser
        onUserChanged(newUser)
    }
}
