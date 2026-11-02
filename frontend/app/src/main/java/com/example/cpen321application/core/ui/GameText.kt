package com.example.cpen321application.core.ui

import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.teamSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// How games are written on screen, shared by the game list, game screen and create form

private val startsAtFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

/**
 * @return e.g. "Mon, Nov 2 · 6:00 PM", in the phone's time zone
 */
fun formatStartsAt(startsAt: Instant): String =
    startsAtFormatter.format(startsAt.atZone(ZoneId.systemDefault()))

val Format.label: String
    get() = when (this) {
        Format.SINGLES -> "Singles"
        Format.DOUBLES -> "Doubles"
    }

val GameStatus.label: String
    get() = when (this) {
        GameStatus.OPEN -> "Open"
        GameStatus.BOOKED -> "Booked"
        GameStatus.COMPLETED -> "Completed"
        GameStatus.CANCELLED -> "Cancelled"
    }

/**
 * @return e.g. "3/4 players"
 */
fun playerCountText(game: Game): String = "${game.players.size}/${game.format.teamSize * 2} players"

/**
 * @return e.g. "14°C · light rain · 40% chance of rain"
 */
fun forecastText(forecast: Forecast): String {
    val temperature = forecast.temperature.roundToInt()
    val rainChance = (forecast.precipitationChance * 100).roundToInt()
    return "$temperature°C · ${forecast.description} · $rainChance% chance of rain"
}
