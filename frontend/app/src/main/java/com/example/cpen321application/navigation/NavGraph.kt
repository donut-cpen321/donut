package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.feature.leaderboard.LeaderboardRoute
import com.example.cpen321application.feature.login.LoginRoute
import com.example.cpen321application.feature.matchmaking.MatchmakingRoute
import com.example.cpen321application.feature.profile.ProfileRoute
import com.example.cpen321application.feature.scheduledgame.ScheduledGameRoute

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Profile : Screen("profile")
    object Matchmaking : Screen("matchmaking")
    object Leaderboard : Screen("leaderboard")
    object ScheduledGame : Screen("scheduled_game")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    serverClientId: String,
    startRoute: String,
    onLoginSuccess: (User) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginRoute(
                serverClientId = serverClientId,
                onLoginSuccess = onLoginSuccess
            )
        }
        composable(Screen.Profile.route) {
            ProfileRoute(onSignOut = onSignOut)
        }
        composable(Screen.Matchmaking.route) {
            MatchmakingRoute()
        }
        composable(Screen.Leaderboard.route) {
            LeaderboardRoute()
        }
        composable(Screen.ScheduledGame.route) {
            ScheduledGameRoute()
        }
    }
}
