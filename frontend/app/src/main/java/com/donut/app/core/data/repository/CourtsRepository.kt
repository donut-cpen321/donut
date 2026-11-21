package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Court
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.dto.CourtDto
import com.donut.app.core.network.dto.toCourt
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import java.io.IOException

interface CourtsRepository {
    // Every court, ordered by name
    suspend fun getCourts(): Result<List<Court>>

    suspend fun getCourts(session: AuthSession): Result<List<Court>> = getCourts()

    suspend fun getPreferredCourts(session: AuthSession): Result<List<Court>> = Result.success(emptyList())

    suspend fun addPreferredCourt(session: AuthSession, courtId: Int): Result<Unit> = Result.success(Unit)

    suspend fun removePreferredCourt(session: AuthSession, courtId: Int): Result<Unit> = Result.success(Unit)
}

class CourtsRepositoryImpl(
    private val session: AuthSession? = null,
    private val apiService: ApiService = RetrofitClient.apiService,
    private val gson: Gson = Gson(),
) : CourtsRepository {

    override suspend fun getCourts(): Result<List<Court>> {
        val s = session ?: return Result.failure(IllegalStateException("You are signed out."))
        return getCourts(s)
    }

    override suspend fun getCourts(session: AuthSession): Result<List<Court>> {
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

    override suspend fun getPreferredCourts(session: AuthSession): Result<List<Court>> {
        return try {
            val jsonElement = session.authorized { apiService.getPreferredCourts(it) }
            if (jsonElement == null) {
                return Result.failure(IllegalStateException("You are signed out."))
            }

            val parsedCourts = parseFlexibleCourts(jsonElement)
            if (parsedCourts.isNotEmpty() && parsedCourts.all { it.name.isNotBlank() }) {
                Result.success(parsedCourts)
            } else {
                val allCourtsResult = getCourts(session)
                val allCourts = allCourtsResult.getOrDefault(emptyList())
                val courtMap = allCourts.associateBy { it.id }

                if (parsedCourts.isNotEmpty()) {
                    val resolved = parsedCourts.mapNotNull { courtMap[it.id] ?: if (it.name.isNotBlank()) it else null }
                    Result.success(resolved)
                } else {
                    val rawIds = parseFlexibleCourtIds(jsonElement)
                    val resolved = rawIds.mapNotNull { courtMap[it] }
                    Result.success(resolved)
                }
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun addPreferredCourt(session: AuthSession, courtId: Int): Result<Unit> {
        return try {
            val response = session.authorized { apiService.addPreferredCourt(it, courtId) }
            if (response != null && (response.isSuccessful || response.code() in 200..299)) {
                Result.success(Unit)
            } else if (response != null) {
                Result.failure(HttpException(response))
            } else {
                Result.failure(Exception("Not authenticated"))
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun removePreferredCourt(session: AuthSession, courtId: Int): Result<Unit> {
        return try {
            val response = session.authorized { apiService.removePreferredCourt(it, courtId) }
            if (response != null && (response.isSuccessful || response.code() in 200..299)) {
                Result.success(Unit)
            } else if (response != null) {
                Result.failure(HttpException(response))
            } else {
                Result.failure(Exception("Not authenticated"))
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun parseFlexibleCourts(jsonElement: JsonElement): List<Court> {
        return try {
            val dtos = parseFlexibleList<CourtDto>(jsonElement, "preferredCourts", "courts")
            dtos.map { it.toCourt() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private val courtIdArrayKeys = listOf("preferredCourts", "courts", "data", "items", "courtIds")

    private fun parseFlexibleCourtIds(jsonElement: JsonElement): List<Int> {
        val arrays: List<JsonArray> = when {
            jsonElement.isJsonArray -> listOf(jsonElement.asJsonArray)
            jsonElement.isJsonObject -> {
                val obj = jsonElement.asJsonObject
                courtIdArrayKeys.mapNotNull { key ->
                    obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
                }
            }
            else -> emptyList()
        }
        return arrays.flatMap { array -> array.mapNotNull { extractCourtId(it) } }
    }

    private fun extractCourtId(element: JsonElement): Int? {
        return try {
            when {
                element.isJsonPrimitive -> element.asString.toIntOrNull()
                element.isJsonObject -> element.asJsonObject.get("id")?.asString?.toIntOrNull()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private inline fun <reified T> parseFlexibleList(
        jsonElement: JsonElement?,
        vararg preferredKeys: String,
    ): List<T> {
        if (jsonElement == null || jsonElement.isJsonNull) return emptyList()

        val type = object : TypeToken<List<T>>() {}.type

        if (jsonElement.isJsonArray) {
            return gson.fromJson(jsonElement, type) ?: emptyList()
        }

        if (jsonElement.isJsonObject) {
            val obj = jsonElement.asJsonObject
            val candidateKeys = preferredKeys.toList() + listOf("data", "items", "results", "list")
            for (key in candidateKeys) {
                if (obj.has(key) && obj.get(key).isJsonArray) {
                    return gson.fromJson(obj.get(key), type) ?: emptyList()
                }
            }
            for (entry in obj.entrySet()) {
                if (entry.value.isJsonArray) {
                    return gson.fromJson(entry.value, type) ?: emptyList()
                }
            }
        }

        return emptyList()
    }
}
