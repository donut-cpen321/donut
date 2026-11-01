package com.example.cpen321application.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ApiErrorsTest {
    private fun httpError(code: Int, body: String) =
        HttpException(Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())))

    // Input: a 409 with the backend's { "error": ... } body
    // Expected output: the backend's error
    @Test
    fun showsTheBackendError() {
        assertEquals(
            "Game starts in less than 30 minutes",
            httpError(409, """{ "error": "Game starts in less than 30 minutes" }""").toUserMessage()
        )
    }

    // Input: a 400 whose details say which check failed
    // Expected output: the error and the first failed check
    @Test
    fun addsTheFirstFieldErrorOfA400() {
        val body = """
            { "error": "Invalid request body",
              "details": { "formErrors": [], "fieldErrors": { "rounds": ["round 2 is tied"] } } }
        """.trimIndent()

        assertEquals("Invalid request body: round 2 is tied", httpError(400, body).toUserMessage())
    }

    // Input: an error body that is not the backend's JSON, e.g. a proxy's HTML page
    // Expected output: a message with the HTTP code
    @Test
    fun fallsBackToTheStatusCode() {
        assertEquals("Request failed (HTTP 502)", httpError(502, "<html>Bad Gateway</html>").toUserMessage())
        assertEquals("Request failed (HTTP 500)", httpError(500, """["not", "an", "object"]""").toUserMessage())
    }

    // Input: the backend could not be reached
    // Expected output: a message about the connection
    @Test
    fun explainsConnectionErrors() {
        assertEquals(
            "Can't reach the server. Check your connection and try again.",
            IOException("timeout").toUserMessage()
        )
    }
}
