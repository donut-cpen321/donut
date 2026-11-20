package com.example.cpen321application.core.ui

import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.teamSize
import java.time.Instant
import java.time.LocalDate
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
        GameStatus.IN_PROGRESS -> "In progress"
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

private val startTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/**
 * @return e.g. "6:00 PM", in the phone's time zone
 */
fun formatStartTime(startsAt: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    startTimeFormatter.format(startsAt.atZone(zone))

/**
 * The day a game falls on, in the phone's time zone
 */
fun startDay(startsAt: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    startsAt.atZone(zone).toLocalDate()

/**
 * @return how many players the game still needs
 */
fun openSpots(game: Game): Int = (game.format.teamSize * 2 - game.players.size).coerceAtLeast(0)

/**
 * @return e.g. "1 spot left" or "3 spots left"
 */
fun openSpotsText(spots: Int): String = if (spots == 1) "1 spot left" else "$spots spots left"

/**
 * @return e.g. "Singles · 1 of 2 players"
 */
fun gameMetaText(game: Game): String =
    "${game.format.label} · ${game.players.size} of ${game.format.teamSize * 2} players"

enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, THUNDER, SNOW, FOG }

/**
 * Picks the weather icon from OpenWeather's description, e.g. "light rain" → RAIN
 */
fun weatherKind(description: String): WeatherKind {
    val text = description.lowercase()
    return when {
        "thunder" in text -> WeatherKind.THUNDER
        "snow" in text || "sleet" in text -> WeatherKind.SNOW
        "rain" in text || "drizzle" in text -> WeatherKind.RAIN
        listOf("mist", "fog", "haze", "smoke", "dust").any { it in text } -> WeatherKind.FOG
        "few clouds" in text || "scattered" in text -> WeatherKind.PARTLY_CLOUDY
        "cloud" in text -> WeatherKind.CLOUDY
        else -> WeatherKind.CLEAR
    }
}

/**
 * @return e.g. "11°"
 */
fun temperatureText(forecast: Forecast): String = "${forecast.temperature.roundToInt()}°"

/**
 * @return e.g. "40%"
 */
fun rainChanceText(forecast: Forecast): String = "${(forecast.precipitationChance * 100).roundToInt()}%"

/**
 * @param separator between the day and the time: ", " in sentences, " · " on a game card
 * @return "Today, 6:30 PM", "Tomorrow, 7:30 AM" or "Sat, Nov 21, 10:00 AM"
 */
fun relativeStartText(
    startsAt: Instant,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    separator: String = ", ",
): String {
    val day = startDay(startsAt, zone)
    val time = formatStartTime(startsAt, zone)
    return when (day) {
        today -> "Today$separator$time"
        today.plusDays(1) -> "Tomorrow$separator$time"
        else -> "${dayFormatter.format(day)}$separator$time"
    }
}
