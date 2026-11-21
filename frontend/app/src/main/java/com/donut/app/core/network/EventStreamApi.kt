package com.donut.app.core.network

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import retrofit2.HttpException
import java.io.IOException

/**
 * One Server-Sent Event
 * @property name the event's name, e.g. "gameUpdated"
 * @property data the event's data, JSON for the backend's streams
 */
data class ServerSentEvent(val name: String, val data: String)

// Status codes from here up are errors, which Retrofit's HttpException can carry
private const val HTTP_ERROR = 400

/**
 * The backend's event streams, e.g. GET /games/events and GET /friends/events: the backend pushes what changed
 * over one long-lived Server-Sent Events response. Retrofit can't stream a response, so this uses OkHttp directly.
 * @param client its read timeout must be longer than the backend's 25 s heartbeat
 * @param baseUrl the backend's base URL
 */
class EventStreamApi(private val client: OkHttpClient, private val baseUrl: HttpUrl) {
    /**
     * Opens a stream and emits each event the backend sends, until the connection ends or the collector stops,
     * which closes the connection. The streams never end on purpose, so any end is reported as an IOException,
     * which the screens reconnect from
     * @param authorization the `Authorization` header value ("Bearer <token>")
     * @param path the stream's route, e.g. "games/events"
     * @param query the route's query parameters, e.g. the game ID
     * @throws HttpException if the backend rejects the stream, e.g. 401 or 404
     * @throws IOException if the connection fails, drops, or is ended by the backend (e.g. restarting)
     */
    fun events(
        authorization: String,
        path: String,
        query: Map<String, String> = emptyMap()
    ): Flow<ServerSentEvent> = callbackFlow {
        val url = baseUrl.newBuilder()
            .addPathSegments(path)
            .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authorization)
            .header("Accept", "text/event-stream")
            .build()

        val streamCall = client.newCall(request)
        streamCall.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            // Runs on an OkHttp thread for as long as the stream stays open
            override fun onResponse(call: Call, response: Response) {
                response.use { close(endOfStream(it)) }
            }
        })

        // Cancelling the call makes the blocked read in readEvents throw, which ends its thread
        awaitClose { streamCall.cancel() }
    }

    // Why the stream ended: the backend's refusal, or an IOException to reconnect from
    private fun ProducerScope<ServerSentEvent>.endOfStream(response: Response): Throwable {
        val body = response.body
        return when {
            body == null -> IOException("Empty response for an event stream")
            response.code >= HTTP_ERROR -> {
                val errorBody = body.string().toResponseBody(body.contentType())
                HttpException(retrofit2.Response.error<Unit>(response.code, errorBody))
            }
            // Not an error, but not the stream either (Retrofit can't wrap it): reconnecting is the best bet
            !response.isSuccessful -> IOException("Unexpected response ${response.code} for an event stream")
            else -> readEvents(body.source())
        }
    }

    // Sends each event to the collector until the stream ends, and returns why it ended: the backend ended it
    // (e.g. restarting, or closing a user's extra streams) or the connection dropped
    private fun ProducerScope<ServerSentEvent>.readEvents(source: BufferedSource): IOException {
        val parser = ServerSentEventParser()
        return try {
            var line = source.readUtf8Line()
            while (line != null) {
                parser.feed(line)?.let { event -> trySendBlocking(event) }
                line = source.readUtf8Line()
            }
            IOException("The backend ended the event stream")
        } catch (e: IOException) {
            e
        }
    }
}

/**
 * Turns the lines of a text/event-stream into events. Comment lines, like the backend's heartbeat, are skipped
 */
class ServerSentEventParser {
    private var name = DEFAULT_EVENT_NAME
    private val data = StringBuilder()
    private var hasData = false

    /**
     * @param line one line of the stream, without its line break
     * @return the finished event if this blank line ends one, otherwise null
     */
    fun feed(line: String): ServerSentEvent? =
        when {
            line.isEmpty() -> finishEvent()
            line.startsWith(":") -> null  // A comment, e.g. the heartbeat
            else -> {
                readField(line)
                null
            }
        }

    // e.g. "event: gameUpdated" or "data: {...}"; other fields (id, retry) aren't used
    private fun readField(line: String) {
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (line.substringBefore(':')) {
            "event" -> name = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
    }

    private fun finishEvent(): ServerSentEvent? {
        val event = if (hasData) ServerSentEvent(name, data.toString()) else null
        name = DEFAULT_EVENT_NAME
        data.clear()
        hasData = false
        return event
    }

    private companion object {
        const val DEFAULT_EVENT_NAME = "message"
    }
}
