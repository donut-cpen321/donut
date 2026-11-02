package com.example.cpen321application.core.network

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

/**
 * Turns a failed backend request into a message to show the user.
 * The backend sends `{ "error": "..." }`, and a 400 also has the failed checks in `details.fieldErrors`.
 * Reads the error body, so call it once per error.
 * @return e.g. "Team is full" or "Invalid request body: round 2 is tied"
 */
fun Throwable.toUserMessage(): String {
    return when (this) {
        is HttpException -> errorBodyMessage(this) ?: "Request failed (HTTP ${code()})"
        is IOException -> "Can't reach the server. Check your connection and try again."
        else -> localizedMessage ?: "Something went wrong."
    }
}

private fun errorBodyMessage(e: HttpException): String? {
    val body = e.response()?.errorBody()?.string() ?: return null

    return try {
        val json = JsonParser.parseString(body).asJsonObject
        val error = json.get("error")?.asString ?: return null

        // e.g. { "fieldErrors": { "rounds": ["round 2 is tied"] } }
        val fieldErrors = json.getAsJsonObject("details")?.getAsJsonObject("fieldErrors")
        val firstFieldError = fieldErrors?.entrySet()?.firstOrNull()?.value?.asJsonArray?.firstOrNull()?.asString

        if (firstFieldError == null) error else "$error: $firstFieldError"
    } catch (_: JsonParseException) {
        null  // Not JSON, e.g. a proxy's HTML error page
    } catch (_: IllegalStateException) {
        null  // JSON, but not the backend's error shape
    }
}
