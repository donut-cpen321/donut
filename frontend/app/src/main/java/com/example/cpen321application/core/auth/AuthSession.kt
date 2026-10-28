package com.example.cpen321application.core.auth

import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.dto.RefreshRequestDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Owns the signed-in user and their tokens.
 * Sends authenticated requests, refreshes the access token when it expires and logs out.
 * @param api the backend routes
 * @param initialUser the user saved from the last session, null if signed out
 * @param onUserChanged called with the new user after sign-in or a refresh, null after sign-out
 */
class AuthSession(
    private val api: ApiService,
    initialUser: User?,
    private val onUserChanged: (User?) -> Unit
) {
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
     * Calls an ApiService route with the access token. If it expired, refreshes it once and retries.
     * @param call the route, given the `Authorization` header value. It must throw HttpException on errors,
     * so return the body type, not Response<T>.
     * @return the route's result, null if no user is signed in
     * @throws HttpException if the backend rejects the request, a 401 if the token could not be refreshed
     * @throws IOException if the backend is unreachable
     */
    suspend fun <T> authorized(call: suspend (authorization: String) -> T): T? {
        val sentAs = user ?: return null
        try {
            return call("Bearer ${sentAs.accessToken}")
        } catch (e: HttpException) {
            if (e.code() != HttpURLConnection.HTTP_UNAUTHORIZED) throw e
            val refreshedUser = refreshTokens(sentAs) ?: throw e
            return call("Bearer ${refreshedUser.accessToken}")
        }
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
            api.logout(RefreshRequestDto(signedOut.refreshToken))
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

        val tokens = try {
            api.refreshTokens(RefreshRequestDto(staleUser.refreshToken))
        } catch (e: HttpException) {
            // Refresh token used, expired or logged out: the user has to sign in with Google again
            if (e.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                setUser(null)
            }
            // Otherwise a server error: keep the session, the next request tries again
            return@withLock null
        }

        val refreshedUser = staleUser.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken)
        setUser(refreshedUser)
        refreshedUser
    }

    private fun setUser(newUser: User?) {
        user = newUser
        onUserChanged(newUser)
    }
}
