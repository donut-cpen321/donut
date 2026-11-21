package com.donut.app.navigation

import androidx.annotation.DrawableRes
import com.donut.app.R

/**
 * A bottom bar tab
 * @property icon the outlined icon, shown when the tab is not open
 * @property selectedIcon the filled icon, shown when it is
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int
) {
    object Matchmaking : BottomNavItem(
        "matchmaking", "Play", R.drawable.ic_sports_tennis, R.drawable.ic_sports_tennis_filled
    )
    object ScheduledGame : BottomNavItem(
        "scheduled_game", "Games", R.drawable.ic_calendar_month, R.drawable.ic_calendar_month_filled
    )
    object Leaderboard : BottomNavItem(
        "leaderboard", "Leaderboard", R.drawable.ic_leaderboard, R.drawable.ic_leaderboard_filled
    )
    object Profile : BottomNavItem("profile", "Profile", R.drawable.ic_person, R.drawable.ic_person_filled)
}
