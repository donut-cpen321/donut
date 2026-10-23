package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginRoute(
                serverClientId = serverClientId,
                onLoginSuccess = { _ ->
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileRoute()
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
