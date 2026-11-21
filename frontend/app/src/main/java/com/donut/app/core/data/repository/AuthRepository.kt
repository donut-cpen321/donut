package com.donut.app.core.data.repository

import com.donut.app.core.auth.User
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.dto.LoginRequestDto
import com.donut.app.core.network.dto.toUser
import retrofit2.HttpException
import java.io.IOException

interface AuthRepository {
    suspend fun loginWithBackend(idToken: String): Result<User>
}

class AuthRepositoryImpl(
    private val apiService: ApiService = RetrofitClient.apiService
) : AuthRepository {

    override suspend fun loginWithBackend(idToken: String): Result<User> {
        return try {
            val response = apiService.loginWithBackend(LoginRequestDto(idToken))
            Result.success(response.toUser())
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
