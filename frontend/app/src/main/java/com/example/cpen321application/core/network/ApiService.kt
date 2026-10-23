package com.example.cpen321application.core.network

import com.example.cpen321application.core.network.dto.LoginRequestDto
import com.example.cpen321application.core.network.dto.UserResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("user/login")
    suspend fun loginWithBackend(@Body request: LoginRequestDto): UserResponseDto
}
