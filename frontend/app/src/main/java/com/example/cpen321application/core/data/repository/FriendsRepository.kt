package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.dto.AcceptFriendRequestDto
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.network.dto.MatchDto
import com.example.cpen321application.core.network.dto.UserMeDto
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import java.io.IOException

interface FriendsRepository {
    suspend fun getUserMe(session: AuthSession): Result<UserMeDto>
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

    override suspend fun getUserMe(session: AuthSession): Result<UserMeDto> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getUserMe(auth)
            }
            val userMe = parseUserMe(jsonElement)
            if (userMe != null) {
                Result.success(userMe)
            } else {
                Result.failure(Exception("Failed to parse user profile"))
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

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
        }
    }

    private fun parseUserMe(jsonElement: JsonElement?): UserMeDto? {
        if (jsonElement == null || jsonElement.isJsonNull) return null

        if (jsonElement.isJsonObject) {
            val obj = jsonElement.asJsonObject

            val targetObj = when {
                obj.has("user") && obj.get("user").isJsonObject -> obj.getAsJsonObject("user")
                obj.has("me") && obj.get("me").isJsonObject -> obj.getAsJsonObject("me")
                obj.has("data") && obj.get("data").isJsonObject -> obj.getAsJsonObject("data")
                else -> obj
            }

            val directDto = try {
                gson.fromJson(targetObj, UserMeDto::class.java)
            } catch (_: Exception) {
                null
            }

            val eloVal = directDto?.elo ?: extractElo(targetObj)
            val idVal = directDto?.id ?: listOf("id", "userId", "_id").firstNotNullOfOrNull { key ->
                if (targetObj.has(key) && !targetObj.get(key).isJsonNull) targetObj.get(key).asString else null
            }
            val usernameVal = directDto?.username ?: listOf("username", "name").firstNotNullOfOrNull { key ->
                if (targetObj.has(key) && !targetObj.get(key).isJsonNull) targetObj.get(key).asString else null
            }
            val emailVal = directDto?.email ?: if (targetObj.has("email") && !targetObj.get("email").isJsonNull) targetObj.get("email").asString else null

            return UserMeDto(id = idVal, username = usernameVal, email = emailVal, elo = eloVal)
        }

        return null
    }

    private fun extractElo(obj: JsonObject): Int? {
        val candidateKeys = listOf("elo", "rating", "eloRating", "rankRating")
        for (key in candidateKeys) {
            if (obj.has(key) && !obj.get(key).isJsonNull) {
                val elem = obj.get(key)
                return try {
                    elem.asInt
                } catch (_: Exception) {
                    try {
                        elem.asDouble.toInt()
                    } catch (_: Exception) {
                        try {
                            elem.asString.toDoubleOrNull()?.toInt()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return null
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
