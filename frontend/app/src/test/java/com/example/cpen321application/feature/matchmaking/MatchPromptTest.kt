package com.example.cpen321application.feature.matchmaking

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchPromptTest {
    private val accepted = Instant.parse("2026-11-02T12:00:00Z")
    private val alice = Player("u1", "alice", "Alice A", 1500.0, 1)
    private val bob = Player("u2", "bob", "Bob B", 1540.0, 2)
    private val carol = Player("u3", "carol", "Carol C", 1480.0, 1)
    private val dan = Player("u4", "dan", "Dan D", 1520.0, 2)

    private fun match(
        players: List<Player>,
        format: Format = Format.SINGLES,
        status: GameStatus = GameStatus.OPEN
    ) = Game(
        id = "game-1",
        format = format,
        status = status,
        source = GameSource.MATCHED,
        startsAt = Instant.parse("2026-11-02T13:00:00Z"),
        court = Court(1, "UBC Tennis Centre", 49.26, -123.25),
        players = players,
        rounds = emptyList()
    )

    // Input: singles match, nobody has accepted, seen by alice
    // Expected output: bob is the opponent, alice hasn't accepted, waiting for bob, not booked
    @Test
    fun singlesShowsTheOtherPlayer() {
        val prompt = matchPrompt(match(listOf(alice, bob)), "alice")

        assertEquals(listOf(bob), prompt.opponents)
        assertEquals(emptyList<Player>(), prompt.partners)
        assertFalse(prompt.hasAccepted)
        assertEquals(listOf(bob), prompt.waitingFor)
        assertFalse(prompt.isBooked)
    }

    // Input: doubles match, alice and carol on team 1, where alice and carol accepted, seen by alice
    // Expected output: carol is the partner, bob and dan the opponents, alice has accepted, waiting for bob and dan
    @Test
    fun doublesListsEveryOtherPlayer() {
        val players = listOf(
            alice.copy(acceptedAt = accepted),
            bob,
            carol.copy(acceptedAt = accepted),
            dan
        )

        val prompt = matchPrompt(match(players, Format.DOUBLES), "alice")

        assertEquals(listOf(carol.copy(acceptedAt = accepted)), prompt.partners)
        assertEquals(listOf(bob, dan), prompt.opponents)
        assertTrue(prompt.hasAccepted)
        assertEquals(listOf(bob, dan), prompt.waitingFor)
    }

    // Input: every player accepted and the game is booked
    // Expected output: booked, waiting for nobody
    @Test
    fun bookedMatchWaitsForNobody() {
        val players = listOf(alice.copy(acceptedAt = accepted), bob.copy(acceptedAt = accepted))

        val prompt = matchPrompt(match(players, status = GameStatus.BOOKED), "alice")

        assertTrue(prompt.isBooked)
        assertEquals(emptyList<Player>(), prompt.waitingFor)
    }

    // Input: a singles match nobody answered, then accepted by alice, then booked
    // Expected output: the title and the line under it follow the answers
    @Test
    fun sheetTextFollowsTheAnswers() {
        val open = matchPrompt(match(listOf(alice, bob)), "alice")
        assertEquals("It's a match!", matchTitle(open))
        assertEquals("Bob (1540) wants a singles game too.", matchSubtitle(open, "Singles"))

        val answered = matchPrompt(match(listOf(alice.copy(acceptedAt = accepted), bob)), "alice")
        assertEquals("You're in!", matchTitle(answered))
        assertEquals("Waiting for Bob to say yes.", matchSubtitle(answered, "Singles"))

        val booked = matchPrompt(match(listOf(alice, bob), status = GameStatus.BOOKED), "alice")
        assertEquals("Game on!", matchTitle(booked))
        assertEquals("Everyone's in. See you on court!", matchSubtitle(booked, "Singles"))
    }

    // Input: a doubles match seen by alice, nobody has answered
    // Expected output: both opponents are named, without ratings
    @Test
    fun doublesSubtitleNamesBothOpponents() {
        val prompt = matchPrompt(match(listOf(alice, bob, carol, dan), Format.DOUBLES), "alice")

        assertEquals("Bob & Dan want a doubles game too.", matchSubtitle(prompt, "Doubles"))
    }

    // Input: minutes until the game starts
    // Expected output: rounded to what a person would say
    @Test
    fun startsInRoundsToWhatPeopleSay() {
        assertEquals("Starting now", startsInText(0))
        assertEquals("In 30 min", startsInText(30))
        assertEquals("An hour from now", startsInText(60))
        assertEquals("In about 2 hours", startsInText(110))
    }
}
