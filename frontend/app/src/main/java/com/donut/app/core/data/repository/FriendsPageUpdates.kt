package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.network.EventStreamApi
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.ServerSentEvent
import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendRequestDto
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

// What the backend pushes about the signed-in user's friends page
sealed interface FriendsPageUpdate {
    // The friends list, with the game each friend is playing right now
    data class Friends(val friends: List<FriendDto>) : FriendsPageUpdate

    // The friend requests waiting for the user to accept them
    data class Requests(val incoming: List<FriendRequestDto>) : FriendsPageUpdate
}

interface FriendsPageUpdates {
    // The friends page pushed by the backend: its current state first, then again each time a friend starts or
    // stops playing, or the friends or requests change. Ends right away if signed out.
    // Throws IOException if the connection drops, HttpException if the backend refuses the stream
    fun updates(session: AuthSession): Flow<FriendsPageUpdate>
}

class FriendsPageUpdatesImpl(
    private val eventStreamApi: EventStreamApi = RetrofitClient.eventStreamApi,
    private val gson: Gson = Gson()
) : FriendsPageUpdates {
    override fun updates(session: AuthSession): Flow<FriendsPageUpdate> =
        session.liveEvents(eventStreamApi, "friends/events", read = ::toUpdate)

    // Null for events this version of the app doesn't know
    private fun toUpdate(event: ServerSentEvent): FriendsPageUpdate? =
        when (event.name) {
            "friends" -> FriendsPageUpdate.Friends(listIn(event.data, "friends"))
            "requests" -> FriendsPageUpdate.Requests(listIn(event.data, "incoming"))
            else -> null
        }

    // The list under a key of a JSON object, e.g. "friends" in { "friends": [...] }
    private inline fun <reified T> listIn(json: String, key: String): List<T> {
        val list = JsonParser.parseString(json).asJsonObject.get(key) ?: return emptyList()
        return gson.fromJson(list, object : TypeToken<List<T>>() {}.type) ?: emptyList()
    }
}
