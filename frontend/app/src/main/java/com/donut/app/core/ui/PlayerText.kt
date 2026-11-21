package com.donut.app.core.ui

// How players are drawn when there is no profile picture

// Four avatar tones: neutral, green, ball yellow, sky
private const val AVATAR_TONES = 4

/**
 * @return up to two initials, e.g. "AR" for "Alex Rivera" or "AS" for "Ana & Sam", "?" for a blank name
 */
fun avatarInitials(name: String): String =
    name.split(' ', '&')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

/**
 * @return which of the four avatar tones a name gets, the same one every time
 */
fun avatarTone(name: String): Int = name.sumOf { it.code } % AVATAR_TONES
