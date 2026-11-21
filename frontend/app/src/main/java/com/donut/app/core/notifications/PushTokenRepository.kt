package com.donut.app.core.notifications

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.dto.FcmTokenRequestDto
import retrofit2.HttpException
import java.io.IOException

/**
 * Tells the backend which device the signed-in user's push notifications go to
 */
class PushTokenRepository(
    private val session: AuthSession,
    private val api: ApiService = RetrofitClient.apiService
) {
    /**
     * @param token the device's FCM token
     * @return failure if nobody is signed in, the backend rejected it, or it is unreachable
     */
    suspend fun register(token: String): Result<Unit> {
        return try {
            session.authorized { authorization -> api.saveFcmToken(authorization, FcmTokenRequestDto(token)) }
                ?: return Result.failure(IllegalStateException("Not signed in"))
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
