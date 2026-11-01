package com.example.cpen321application.core.network

import com.example.cpen321application.core.network.dto.LoginRequestDto
import com.example.cpen321application.core.network.dto.RefreshRequestDto
import com.example.cpen321application.core.network.dto.TokensResponseDto
import com.example.cpen321application.core.network.dto.UserResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("user/login")
    suspend fun loginWithBackend(@Body request: LoginRequestDto): UserResponseDto

    @POST("user/refresh")
    suspend fun refreshTokens(@Body request: RefreshRequestDto): TokensResponseDto

    @POST("user/logout")
    suspend fun logout(@Body request: RefreshRequestDto): Response<Unit>
}
