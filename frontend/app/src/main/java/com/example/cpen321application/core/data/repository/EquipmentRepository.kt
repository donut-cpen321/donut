package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.dto.CreateEquipmentRequestDto
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.UpdateEquipmentRequestDto
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import java.io.IOException

interface EquipmentRepository {
    suspend fun getEquipment(session: AuthSession): Result<List<EquipmentDto>>
    suspend fun createEquipment(session: AuthSession, request: CreateEquipmentRequestDto): Result<EquipmentDto>
    suspend fun updateEquipment(session: AuthSession, id: Int, request: UpdateEquipmentRequestDto): Result<EquipmentDto>
    suspend fun deleteEquipment(session: AuthSession, id: Int): Result<Unit>
}

class EquipmentRepositoryImpl(
    private val apiService: ApiService = RetrofitClient.apiService,
    private val gson: Gson = Gson(),
) : EquipmentRepository {

    override suspend fun getEquipment(session: AuthSession): Result<List<EquipmentDto>> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getEquipment(auth)
            }
            val equipment = parseFlexibleList<EquipmentDto>(jsonElement, "equipment")
            Result.success(equipment)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun createEquipment(
        session: AuthSession,
        request: CreateEquipmentRequestDto,
    ): Result<EquipmentDto> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.createEquipment(auth, request)
            }
            val equipment = parseSingleEquipment(jsonElement)
            if (equipment != null) {
                Result.success(equipment)
            } else {
                Result.failure(Exception("Failed to parse created equipment"))
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun updateEquipment(
        session: AuthSession,
        id: Int,
        request: UpdateEquipmentRequestDto,
    ): Result<EquipmentDto> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.updateEquipment(auth, id, request)
            }
            val equipment = parseSingleEquipment(jsonElement)
            if (equipment != null) {
                Result.success(equipment)
            } else {
                Result.failure(Exception("Failed to parse updated equipment"))
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEquipment(session: AuthSession, id: Int): Result<Unit> {
        return try {
            val response = session.authorized { auth ->
                apiService.deleteEquipment(auth, id)
            }
            if (response != null && response.isSuccessful) {
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

    private fun parseSingleEquipment(jsonElement: JsonElement?): EquipmentDto? {
        if (jsonElement == null || jsonElement.isJsonNull) return null

        if (jsonElement.isJsonObject) {
            val obj = jsonElement.asJsonObject
            val targetObj = when {
                obj.has("equipment") && obj.get("equipment").isJsonObject -> obj.getAsJsonObject("equipment")
                obj.has("data") && obj.get("data").isJsonObject -> obj.getAsJsonObject("data")
                else -> obj
            }
            return try {
                gson.fromJson(targetObj, EquipmentDto::class.java)
            } catch (_: Exception) {
                null
            }
        }

        return null
    }
}
