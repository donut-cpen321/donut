package com.example.cpen321application.core.auth

import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.dto.RefreshRequestDto
import com.example.cpen321application.core.network.dto.TokensResponseDto
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

    // The signed-in user, null if signed out. Anyone can read it, only this class changes it.
    // @Volatile because requests on different threads read it.
    @Volatile
    var user: User? = initialUser
        private set

    /**
     * Starts a session with the user returned by loginWithBackend
     */
    fun signIn(newUser: User) {
        setUser(newUser)
    }

    /**
     * Calls an ApiService route with the access token. If it expired, refreshes it once and retries.
     *
     * Example: `session.authorized { authorization -> api.getGames(authorization) }`
     *
     * @param call the route to call. It gets the `Authorization` header value ("Bearer <token>").
     * It must throw HttpException on errors, so the route returns the body type, not Response<T>.
     * @return the route's result, null if no user is signed in
     * @throws HttpException if the backend rejects the request, a 401 if the token could not be refreshed
     * @throws IOException if the backend is unreachable
     */
    suspend fun <T> authorized(call: suspend (authorization: String) -> T): T? {
        val currentUser = user
        if (currentUser == null) {
            return null
        }

        try {
            return call("Bearer " + currentUser.accessToken)
        } catch (e: HttpException) {
            if (e.code() != HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw e  // Not a token problem, the caller handles it
            }

            val refreshedUser = refreshTokens(currentUser)
            if (refreshedUser == null) {
                throw e  // Could not refresh, pass on the original 401
            }

            return call("Bearer " + refreshedUser.accessToken)
        }
    }

    /**
     * Signs out on this device and deletes the refresh token on the backend.
     * The user is signed out even if the backend is unreachable.
     */
    suspend fun logout() {
        val signedOutUser = signOutOnDevice()
        if (signedOutUser == null) {
            return  // Nobody was signed in
        }

        try {
            api.logout(RefreshRequestDto(signedOutUser.refreshToken))
        } catch (e: IOException) {
            // Already signed out here; the refresh token expires on its own
        }
    }

    private suspend fun signOutOnDevice(): User? {
        lock.withLock {
            val currentUser = user
            if (currentUser != null) {
                setUser(null)
            }
            return currentUser
        }
    }

    private suspend fun refreshTokens(staleUser: User): User? {
        lock.withLock {
            val currentUser = user
            if (currentUser != staleUser) {
                // Another request already refreshed, or the user signed out, while this one waited
                return currentUser
            }

            val tokens: TokensResponseDto
            try {
                tokens = api.refreshTokens(RefreshRequestDto(staleUser.refreshToken))
            } catch (e: HttpException) {
                if (e.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    // Refresh token used, expired or logged out: the user has to sign in with Google again
                    setUser(null)
                }
                // Otherwise a server error: keep the session, the next request tries again
                return null
            }

            val refreshedUser = User(staleUser.username, tokens.accessToken, tokens.refreshToken)
            setUser(refreshedUser)
            return refreshedUser
        }
    }

    private fun setUser(newUser: User?) {
        user = newUser
        onUserChanged(newUser)
    }
}
