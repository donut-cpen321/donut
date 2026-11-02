package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cpen321application.core.auth.AuthViewModel
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

data class NavGraphArgs(
    val serverClientId: String,
    val startRoute: String,
    val authViewModel: AuthViewModel,
    val onLoginSuccess: (User) -> Unit,
    val onSignOut: () -> Unit,
)

@Composable
fun AppNavGraph(
    navController: NavHostController,
    args: NavGraphArgs,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = args.startRoute,
        modifier = modifier,
    ) {
        composable(Screen.Login.route) {
            LoginRoute(
                serverClientId = args.serverClientId,
                onLoginSuccess = args.onLoginSuccess,
            )
        }
        composable(Screen.Profile.route) {
            ProfileRoute(
                authViewModel = args.authViewModel,
                onSignOut = args.onSignOut,
            )
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
