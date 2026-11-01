package com.example.cpen321application.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Profile : BottomNavItem("profile", "Profile", Icons.Default.Person)
    object Matchmaking : BottomNavItem("matchmaking", "Matchmaking", Icons.Default.Search)
    object Leaderboard : BottomNavItem("leaderboard", "Leaderboard", Icons.AutoMirrored.Filled.List)
    object ScheduledGame : BottomNavItem("scheduled_game", "Games", Icons.Default.DateRange)
}
