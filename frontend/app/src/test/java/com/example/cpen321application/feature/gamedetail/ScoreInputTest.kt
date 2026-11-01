package com.example.cpen321application.feature.gamedetail

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
