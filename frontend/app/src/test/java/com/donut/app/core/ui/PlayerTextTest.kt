package com.donut.app.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTextTest {

    @Test
    fun initialsAreFirstLettersOfTheFirstTwoWords() {
        assertEquals("AR", avatarInitials("Alex Rivera"))
        assertEquals("M", avatarInitials("maya"))
        assertEquals("MV", avatarInitials("Maria von Trapp"))
    }

    @Test
    fun initialsOfATeamUseEachPlayer() {
        assertEquals("AS", avatarInitials("Ana & Sam"))
    }

    @Test
    fun blankNameGetsAPlaceholder() {
        assertEquals("?", avatarInitials("  "))
    }

    @Test
    fun toneIsStableAndInRange() {
        assertEquals(avatarTone("Jonah Brooks"), avatarTone("Jonah Brooks"))
        listOf("Alex Rivera", "Maya Chen", "Kenji Watanabe", "").forEach { assertTrue(avatarTone(it) in 0..3) }
    }
}
