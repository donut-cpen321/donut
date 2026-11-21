package com.donut.app.feature.gamedetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreInputTest {
    // Input: a three-set score team 1 won 2-1, with spaces around one number
    // Expected output: no error, and the scores as (team 1, team 2) pairs
    @Test
    fun acceptsAScoreWithAWinner() {
        val rounds = listOf(RoundInput("6", "4"), RoundInput("3", "6"), RoundInput(" 7", "5 "))

        assertNull(scoreError(rounds))
        assertEquals(listOf(Pair(6, 4), Pair(3, 6), Pair(7, 5)), toScores(rounds))
    }

    // Input: a single set
    // Expected output: no error
    @Test
    fun acceptsASingleSet() {
        assertNull(scoreError(listOf(RoundInput("0", "6"))))
    }

    // Input: no sets
    // Expected output: asks for at least one set
    @Test
    fun rejectsNoSets() {
        assertEquals("Add at least one set.", scoreError(emptyList()))
    }

    // Input: a set with an empty, non-numeric or negative score
    // Expected output: says which set to fix
    @Test
    fun rejectsSetsThatAreNotWholeNumbers() {
        val expected = "Set 2: enter a whole number for each team."

        assertEquals(expected, scoreError(listOf(RoundInput("6", "4"), RoundInput("", "6"))))
        assertEquals(expected, scoreError(listOf(RoundInput("6", "4"), RoundInput("six", "6"))))
        assertEquals(expected, scoreError(listOf(RoundInput("6", "4"), RoundInput("-1", "6"))))
    }

    // Input: the second set is tied
    // Expected output: says which set is tied
    @Test
    fun rejectsATiedSet() {
        assertEquals(
            "Set 2 is tied. A set needs a winner.",
            scoreError(listOf(RoundInput("6", "4"), RoundInput("5", "5"), RoundInput("6", "2")))
        )
    }

    // Input: sets that can happen in tennis: 7–5, a 7–6 tiebreak set, and long advantage sets won by 2
    // Expected output: no error
    @Test
    fun acceptsSetsWonBy2PastSixGamesOrSevenSix() {
        assertNull(scoreError(listOf(RoundInput("7", "5"), RoundInput("6", "7"), RoundInput("7", "6"))))
        assertNull(scoreError(listOf(RoundInput("8", "6"), RoundInput("97", "99"), RoundInput("99", "97"))))
    }

    // Input: sets past 6 games that weren't won by 2 and aren't 7–6: the 99–0 blowout from the review, 9–3 and 8–5
    // Expected output: says which set to fix and what a set past 6 games looks like
    @Test
    fun rejectsSetsPastSixGamesNotWonBy2() {
        val expected = { set: Int -> "Set $set: past 6 games, a set is won by 2 games, or 7–6." }

        assertEquals(expected(1), scoreError(listOf(RoundInput("99", "0"), RoundInput("6", "4"))))
        assertEquals(expected(2), scoreError(listOf(RoundInput("6", "4"), RoundInput("9", "3"))))
        assertEquals(expected(2), scoreError(listOf(RoundInput("6", "4"), RoundInput("5", "8"), RoundInput("6", "1"))))
    }

    // Input: each team won one set
    // Expected output: explains there is no winner
    @Test
    fun rejectsAScoreWithoutAWinner() {
        assertEquals(
            "Both teams won the same number of sets, so there is no winner.",
            scoreError(listOf(RoundInput("6", "4"), RoundInput("4", "6")))
        )
    }
}
