package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.dto.toCourt
import retrofit2.HttpException
import java.io.IOException

interface CourtsRepository {
    // Every court, ordered by name
    suspend fun getCourts(): Result<List<Court>>
}

class CourtsRepositoryImpl(
    private val session: AuthSession,
    private val apiService: ApiService = RetrofitClient.apiService
) : CourtsRepository {

    override suspend fun getCourts(): Result<List<Court>> {
        return try {
            val courts = session.authorized { apiService.getCourts(it).courts.map { court -> court.toCourt() } }
            if (courts == null) {
                Result.failure(IllegalStateException("You are signed out."))
            } else {
                Result.success(courts)
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
