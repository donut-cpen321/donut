package com.donut.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

// Play first: finding a match is what the app is for
private val TABS = listOf(
    BottomNavItem.Matchmaking,
    BottomNavItem.ScheduledGame,
    BottomNavItem.Leaderboard,
    BottomNavItem.Profile
)

@Composable
fun AppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val colors = MaterialTheme.colorScheme

    // A hairline instead of a tinted bar; the open tab is the filled icon, with no pill behind it
    Column {
        HorizontalDivider(color = colors.outlineVariant)
        NavigationBar(containerColor = colors.background, tonalElevation = NavigationBarFlat) {
            TABS.forEach { item ->
                val isSelected = currentRoute == item.route

                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
                            contentDescription = null
                        )
                    },
                    label = { Text(item.title, style = MaterialTheme.typography.labelMedium) },
                    selected = isSelected,
                    onClick = {
                        if (currentRoute != item.route) {
                            navController.navigateToTab(item.route)
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onSurface,
                        selectedTextColor = colors.onSurface,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

private val NavigationBarFlat = 0.dp

/**
 * Opens a bottom bar tab the way tapping it does: one copy of each tab, each keeping its state
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
