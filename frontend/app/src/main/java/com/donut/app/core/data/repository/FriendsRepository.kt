package com.donut.app.core.data.repository

import android.util.Log
import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Game
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.GamesApi
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.dto.AcceptFriendRequestDto
import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendRequestDto
import com.donut.app.core.network.dto.SendFriendRequestDto
import com.donut.app.core.network.dto.UserMeDto
import com.donut.app.core.network.dto.toGame
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

interface FriendsRepository {
    suspend fun getUserMe(session: AuthSession): Result<UserMeDto>
    suspend fun getFriends(session: AuthSession): Result<List<FriendDto>>
    suspend fun removeFriend(session: AuthSession, userId: String): Result<Unit>
    suspend fun getFriendRequests(session: AuthSession): Result<List<FriendRequestDto>>
    suspend fun acceptFriendRequest(session: AuthSession, userId: String): Result<Unit>

    // The requests the signed-in user sent that are still pending
    suspend fun getOutgoingFriendRequests(session: AuthSession): Result<List<FriendRequestDto>>

    // Sends a friend request. If the other user already sent one, the backend accepts theirs instead
    suspend fun sendFriendRequest(session: AuthSession, userId: String): Result<Unit>

    // The signed-in user's completed games, most recent first
    suspend fun getMatchHistory(session: AuthSession): Result<List<Game>>

    // Fetches the user's profile photo URL from GET users/:userId/photo
    suspend fun getUserPhoto(session: AuthSession, userId: String): Result<String?>
}

fun safeLog(tag: String, msg: String) {
    try {
        Log.d(tag, msg)
    } catch (_: Throwable) {
        println("[$tag] $msg")
    }
}

object UserPhotoCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun get(userId: String): String? = cache[userId]

    fun put(userId: String, photoUrl: String?) {
        if (!photoUrl.isNullOrBlank()) {
            cache[userId] = photoUrl
        }
    }

    fun clear() {
        cache.clear()
    }
}

class FriendsRepositoryImpl(
    private val apiService: ApiService = RetrofitClient.apiService,
    private val gamesApi: GamesApi = RetrofitClient.gamesApi,
    private val gson: Gson = Gson()
) : FriendsRepository {

    override suspend fun getUserMe(session: AuthSession): Result<UserMeDto> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getUserMe(auth)
            }
            val userMe = parseUserMe(jsonElement)
            if (userMe != null) {
                userMe.id?.let { id -> UserPhotoCache.put(id, userMe.avatarUrl) }
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
            friends.forEach { friend ->
                UserPhotoCache.put(friend.userId, friend.avatarUrl)
            }
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
            // The backend sends { incoming, outgoing }; this list is the requests to accept
            val requests = parseFlexibleList<FriendRequestDto>(jsonElement, "incoming", "requests", "friendRequests")
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

    override suspend fun getOutgoingFriendRequests(session: AuthSession): Result<List<FriendRequestDto>> {
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getFriendRequests(auth)
            }
            val outgoing = jsonElement
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("outgoing")
                ?.takeIf { it.isJsonArray }
            val requests: List<FriendRequestDto> = if (outgoing == null) {
                emptyList()
            } else {
                gson.fromJson(outgoing, object : TypeToken<List<FriendRequestDto>>() {}.type)
            }
            Result.success(requests)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun sendFriendRequest(session: AuthSession, userId: String): Result<Unit> {
        return try {
            val response = session.authorized { auth ->
                apiService.sendFriendRequest(auth, SendFriendRequestDto(userId))
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

    override suspend fun getMatchHistory(session: AuthSession): Result<List<Game>> {
        return try {
            val response = session.authorized { auth ->
                gamesApi.getGameHistory(auth)
            }
            if (response == null) {
                Result.failure(Exception("Not authenticated"))
            } else {
                Result.success(response.games.map { it.toGame() })
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun getUserPhoto(session: AuthSession, userId: String): Result<String?> {
        UserPhotoCache.get(userId)?.let { cachedUrl ->
            return Result.success(cachedUrl)
        }
        return try {
            val jsonElement = session.authorized { auth ->
                apiService.getUserPhoto(auth, userId)
            }
            val photoUrl = parsePhotoUrl(jsonElement)
            if (!photoUrl.isNullOrBlank()) {
                UserPhotoCache.put(userId, photoUrl)
            }
            Result.success(photoUrl)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private val USER_WRAPPER_KEYS = listOf("user", "me", "data")
    private val USER_ID_KEYS = listOf("id", "userId", "_id")
    private val USERNAME_KEYS = listOf("username", "name")

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.asString

    private fun parseUserMe(jsonElement: JsonElement?): UserMeDto? {
        if (jsonElement == null || !jsonElement.isJsonObject) return null

        val obj = jsonElement.asJsonObject
        val target = USER_WRAPPER_KEYS.firstNotNullOfOrNull { key ->
            obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        } ?: obj

        val directDto = try {
            gson.fromJson(target, UserMeDto::class.java)
        } catch (_: Exception) {
            null
        }

        return UserMeDto(
            id = directDto?.id ?: USER_ID_KEYS.firstNotNullOfOrNull { target.stringOrNull(it) },
            username = directDto?.username ?: USERNAME_KEYS.firstNotNullOfOrNull { target.stringOrNull(it) },
            name = directDto?.name,
            email = directDto?.email ?: target.stringOrNull("email"),
            elo = directDto?.elo ?: extractElo(target),
            avatarUrl = directDto?.avatarUrl ?: parsePhotoUrl(target),
        )
    }

    private val PHOTO_WRAPPER_KEYS = listOf("data", "user", "photo")
    private val PHOTO_URL_KEYS =
        listOf("photoUrl", "url", "photo", "picture", "avatarUrl", "imageUrl", "googleImage", "link")

    private fun parsePhotoUrl(jsonElement: JsonElement?): String? {
        return when {
            jsonElement == null || jsonElement.isJsonNull -> null
            jsonElement.isJsonPrimitive -> asHttpUrl(jsonElement)
            jsonElement.isJsonObject -> {
                val obj = jsonElement.asJsonObject
                val target = PHOTO_WRAPPER_KEYS.firstNotNullOfOrNull { key ->
                    obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
                } ?: obj
                PHOTO_URL_KEYS.firstNotNullOfOrNull { key -> asHttpUrl(target.get(key)) }
            }
            else -> null
        }
    }

    // Returns the trimmed string if the element is a primitive holding an http(s) URL
    private fun asHttpUrl(element: JsonElement?): String? {
        if (element == null || !element.isJsonPrimitive) return null
        val str = element.asString.trim()
        return if (str.startsWith("http://") || str.startsWith("https://")) str else null
    }

    private val ELO_KEYS = listOf("elo", "rating", "eloRating", "rankRating")

    private fun extractElo(obj: JsonObject): Int? {
        val elem = ELO_KEYS
            .firstNotNullOfOrNull { key -> obj.get(key)?.takeUnless { it.isJsonNull } }
            ?: return null
        return parseEloElement(elem)
    }

    // Rounded like the leaderboard and game screens (1584.6 → 1585). asInt would cut it to 1584
    private fun parseEloElement(elem: JsonElement): Int? =
        runCatching { elem.asDouble.roundToInt() }.getOrNull()
            ?: runCatching { elem.asString.toDoubleOrNull()?.roundToInt() }.getOrNull()

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