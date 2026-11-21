package com.donut.app.core.network

import com.donut.app.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val baseUrl: String by lazy {
        BuildConfig.API_BASE_URL.let {
            if (it.endsWith("/")) it else "$it/"
        }
    }

    // The backend sends a heartbeat every 25 s on an event stream, so a minute of silence means the connection died
    private const val STREAM_READ_TIMEOUT_SECONDS = 60L

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }

    // The /games routes, on the same client as apiService
    val gamesApi: GamesApi by lazy { retrofit.create(GamesApi::class.java) }

    // The event streams (GET /games/events, GET /friends/events), which push changes as they happen
    val eventStreamApi: EventStreamApi by lazy {
        val client = OkHttpClient.Builder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        EventStreamApi(client, baseUrl.toHttpUrl())
    }

    // The /tournaments routes, on the same client as apiService
    val tournamentApi: TournamentApi by lazy { retrofit.create(TournamentApi::class.java) }

    // The /stats route, on the same client as apiService
    val statsApi: StatsApi by lazy { retrofit.create(StatsApi::class.java) }
}
