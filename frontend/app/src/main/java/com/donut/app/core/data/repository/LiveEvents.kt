package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.network.EventStreamApi
import com.donut.app.core.network.ServerSentEvent
import com.google.gson.JsonParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.retryWhen
import retrofit2.HttpException
import java.io.IOException

// Wait before reconnecting to an event stream, so a backend that is down isn't hammered
const val RECONNECT_DELAY_MS = 3_000L

/**
 * Follows one of the backend's event streams as the signed-in user, turning each event into a T. A 401 when
 * connecting makes authorized refresh the token and connect again. Ends right away if signed out
 * @param api the event streams
 * @param path the stream's route, e.g. "games/events"
 * @param query the route's query parameters, e.g. the game ID
 * @param read turns an event into a T. Events it returns null for, or can't read (malformed, or with an enum
 * value this version of the app doesn't know), are skipped
 * @throws IOException if the connection drops or the backend ends it
 * @throws HttpException if the backend refuses the stream
 */
fun <T : Any> AuthSession.liveEvents(
    api: EventStreamApi,
    path: String,
    query: Map<String, String> = emptyMap(),
    read: (ServerSentEvent) -> T?
): Flow<T> = flow {
    authorized { authorization ->
        emitAll(api.events(authorization, path, query).mapNotNull { event -> readOrSkip(event, read) })
    }
}

// What read makes of the event, or null (skipped) if it can't be read
private fun <T> readOrSkip(event: ServerSentEvent, read: (ServerSentEvent) -> T?): T? =
    try {
        read(event)
    } catch (e: JsonParseException) {
        null
    } catch (e: IllegalArgumentException) {
        null  // An enum value this version of the app doesn't know
    } catch (e: IllegalStateException) {
        null  // Not the JSON shape expected, e.g. not an object
    }

/**
 * Keeps a screen's live stream going: reconnects after the connection drops or the backend ends it (the backend
 * sends the current state again on connect, so nothing is missed), and calls onRefused instead of failing when
 * the backend refuses it, e.g. the game is gone
 * @param onRefused shows the refusal, e.g. as the screen's error
 */
fun <T> Flow<T>.reconnecting(onRefused: (HttpException) -> Unit): Flow<T> =
    retryWhen { cause, _ ->
        val reconnect = cause is IOException
        if (reconnect) delay(RECONNECT_DELAY_MS)
        reconnect
    }.catch { error ->
        if (error !is HttpException) throw error
        onRefused(error)
    }
