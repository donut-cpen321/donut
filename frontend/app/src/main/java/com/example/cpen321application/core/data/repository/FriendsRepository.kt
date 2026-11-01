package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.dto.AcceptFriendRequestDto
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.network.dto.MatchDto
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import java.io.IOException

interface FriendsRepository {
    suspend fun getFriends(session: AuthSession): Result<List<FriendDto>>
    suspend fun removeFriend(session: AuthSession, userId: String): Result<Unit>
    suspend fun getFriendRequests(session: AuthSession): Result<List<FriendRequestDto>>
    suspend fun acceptFriendRequest(session: AuthSession, userId: String): Result<Unit>
    suspend fun getMatchHistory(session: AuthSession): Result<List<MatchDto>>
}

class FriendsRepositoryImpl(
    private val apiService: ApiService = RetrofitClient.apiService,
    private val gson: Gson = Gson()
) : FriendsRepository {

    override suspend fun getFriends(session: AuthSession): Result<List<FriendDto>> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getFriends(auth)
            }
            val friends = parseFlexibleList<FriendDto>(jsonElement, "friends")
            Result.success(friends)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFriend(session: AuthSession, userId: String): Result<Unit> {
        return try {
            val response = session.authorized { auth ->
                apiService.removeFriend(auth, userId)
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFriendRequests(session: AuthSession): Result<List<FriendRequestDto>> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getFriendRequests(auth)
            }
            val requests = parseFlexibleList<FriendRequestDto>(jsonElement, "requests", "friendRequests")
            Result.success(requests)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptFriendRequest(session: AuthSession, userId: String): Result<Unit> {
        return try {
            val response = session.authorized { auth ->
                apiService.acceptFriendRequest(auth, AcceptFriendRequestDto(userId))
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMatchHistory(session: AuthSession): Result<List<MatchDto>> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getMatchHistory(auth)
            }
            val matches = parseFlexibleList<MatchDto>(jsonElement, "matches", "matchHistory", "history")
            Result.success(matches)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private inline fun <reified T> parseFlexibleList(
        jsonElement: JsonElement?,
        vararg preferredKeys: String
    ): List<T> {
        if (jsonElement == null || jsonElement.isJsonNull) return emptyList()

        val type = object : TypeToken<List<T>>() {}.type

        // Case 1: Direct JSON Array e.g. [ {...}, {...} ]
        if (jsonElement.isJsonArray) {
            return gson.fromJson(jsonElement, type) ?: emptyList()
        }

        // Case 2: JSON Object wrapper e.g. { "friends": [ ... ] } or { "data": [ ... ] }
        if (jsonElement.isJsonObject) {
            val obj = jsonElement.asJsonObject

            // Check preferred keys first ("friends", "requests", "matches", etc.)
            val candidateKeys = preferredKeys.toList() + listOf("data", "items", "results", "list")
            for (key in candidateKeys) {
                if (obj.has(key) && obj.get(key).isJsonArray) {
                    return gson.fromJson(obj.get(key), type) ?: emptyList()
                }
            }

            // Fallback: look for any key whose value is a JsonArray
            for (entry in obj.entrySet()) {
                if (entry.value.isJsonArray) {
                    return gson.fromJson(entry.value, type) ?: emptyList()
                }
            }
        }

        return emptyList()
    }
}
