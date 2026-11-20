package com.example.cpen321application.core.ui

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GameTextTest {

    private val utc = ZoneOffset.UTC

    // Dates and times follow the phone's locale ("6:00 PM" in en-US, "6:00 p.m." in en-CA),
    // so the expected text is built with the same patterns
    private fun time(at: String) = DateTimeFormatter.ofPattern("h:mm a").format(Instant.parse(at).atZone(utc))
    private fun day(date: LocalDate) = DateTimeFormatter.ofPattern("EEE, MMM d").format(date)

    private fun player(id: String) = Player(id = id, username = id, name = id, rating = 1500.0, team = 1)

    private fun game(format: Format, players: Int) = Game(
        id = "g1",
        format = format,
        status = GameStatus.OPEN,
        source = GameSource.SCHEDULED,
        startsAt = Instant.parse("2026-11-16T18:00:00Z"),
        court = Court(1, "Kitsilano Beach Park", 49.27, -123.15),
        players = (1..players).map { player("p$it") },
        rounds = emptyList()
    )

    @Test
    fun startTimeHasNoDay() {
        assertEquals(time("2026-11-16T18:00:00Z"), formatStartTime(Instant.parse("2026-11-16T18:00:00Z"), utc))
        assertTrue(formatStartTime(Instant.parse("2026-11-16T18:00:00Z"), utc).startsWith("6:00"))
    }

    @Test
    fun startDayUsesTheGivenZone() {
        // 2 AM UTC on the 17th is still the 16th in Vancouver
        val startsAt = Instant.parse("2026-11-17T02:00:00Z")

        assertEquals(LocalDate.of(2026, 11, 17), startDay(startsAt, utc))
        assertEquals(LocalDate.of(2026, 11, 16), startDay(startsAt, ZoneOffset.ofHours(-8)))
    }

    @Test
    fun relativeStartSaysTodayOrTheDate() {
        val today = LocalDate.of(2026, 11, 16)

        assertEquals(
            "Today, ${time("2026-11-16T18:00:00Z")}",
            relativeStartText(Instant.parse("2026-11-16T18:00:00Z"), today, utc)
        )
        assertEquals(
            "Tomorrow, ${time("2026-11-17T07:30:00Z")}",
            relativeStartText(Instant.parse("2026-11-17T07:30:00Z"), today, utc)
        )
        assertEquals(
            "${day(LocalDate.of(2026, 11, 21))}, ${time("2026-11-21T10:00:00Z")}",
            relativeStartText(Instant.parse("2026-11-21T10:00:00Z"), today, utc)
        )
    }

    @Test
    fun cardStartPutsADotBetweenDayAndTime() {
        val today = LocalDate.of(2026, 11, 16)

        assertEquals(
            "Today · ${time("2026-11-16T18:00:00Z")}",
            relativeStartText(Instant.parse("2026-11-16T18:00:00Z"), today, utc, separator = " · ")
        )
        assertEquals(
            "${day(LocalDate.of(2026, 11, 21))} · ${time("2026-11-21T10:00:00Z")}",
            relativeStartText(Instant.parse("2026-11-21T10:00:00Z"), today, utc, separator = " · ")
        )
    }

    @Test
    fun openSpotsCountsWhatTheFormatNeeds() {
        assertEquals(1, openSpots(game(Format.SINGLES, players = 1)))
        assertEquals(3, openSpots(game(Format.DOUBLES, players = 1)))
        assertEquals(0, openSpots(game(Format.DOUBLES, players = 4)))
    }

    @Test
    fun openSpotsTextIsSingularForOne() {
        assertEquals("1 spot left", openSpotsText(1))
        assertEquals("3 spots left", openSpotsText(3))
    }

    @Test
    fun metaTextShowsFormatAndPlayers() {
        assertEquals("Doubles · 3 of 4 players", gameMetaText(game(Format.DOUBLES, players = 3)))
    }

    @Test
    fun weatherKindReadsOpenWeatherDescriptions() {
        assertEquals(WeatherKind.CLEAR, weatherKind("clear sky"))
        assertEquals(WeatherKind.PARTLY_CLOUDY, weatherKind("few clouds"))
        assertEquals(WeatherKind.PARTLY_CLOUDY, weatherKind("scattered clouds"))
        assertEquals(WeatherKind.CLOUDY, weatherKind("overcast clouds"))
        assertEquals(WeatherKind.RAIN, weatherKind("Light Rain"))
        assertEquals(WeatherKind.RAIN, weatherKind("drizzle"))
        assertEquals(WeatherKind.THUNDER, weatherKind("thunderstorm with rain"))
        assertEquals(WeatherKind.SNOW, weatherKind("light snow"))
        assertEquals(WeatherKind.FOG, weatherKind("mist"))
    }

    @Test
    fun forecastPartsAreRounded() {
        val forecast = Forecast(temperature = 10.6, description = "rain", precipitationChance = 0.404)

        assertEquals("11°", temperatureText(forecast))
        assertEquals("40%", rainChanceText(forecast))
    }
}
