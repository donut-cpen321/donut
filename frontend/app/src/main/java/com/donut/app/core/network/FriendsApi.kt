package com.donut.app.core.network

import com.donut.app.core.network.dto.AcceptFriendRequestDto
import com.donut.app.core.network.dto.SendFriendRequestDto
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The /friends routes. Separate from ApiService, which already has many routes.
 */
interface FriendsApi {
    @GET("friends")
    suspend fun getFriends(@Header("Authorization") authorization: String): JsonElement

    // Removes a friend, or declines or cancels a pending request
    @DELETE("friends")
    suspend fun removeFriend(
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String,
    ): Response<ResponseBody>

    @GET("friends/requests")
    suspend fun getFriendRequests(@Header("Authorization") authorization: String): JsonElement

    @POST("friends/requests/accept")
    suspend fun acceptFriendRequest(
        @Header("Authorization") authorization: String,
        @Body request: AcceptFriendRequestDto,
    ): Response<ResponseBody>

    // Sends a friend request, or accepts the other user's pending request to this user
    @POST("friends/requests")
    suspend fun sendFriendRequest(
        @Header("Authorization") authorization: String,
        @Body request: SendFriendRequestDto,
    ): Response<ResponseBody>
}
