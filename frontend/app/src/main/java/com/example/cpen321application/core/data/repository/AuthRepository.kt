package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.dto.LoginRequestDto

interface AuthRepository {
    suspend fun loginWithBackend(idToken: String): Result<User>
}

class AuthRepositoryImpl(
    private val apiService: ApiService = RetrofitClient.apiService
) : AuthRepository {

    override suspend fun loginWithBackend(idToken: String): Result<User> {
        return try {
            val response = apiService.loginWithBackend(LoginRequestDto(idToken))
            Result.success(User(username = response.username))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
