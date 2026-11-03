package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.auth.AuthViewModel
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.feature.leaderboard.LeaderboardRoute
import com.example.cpen321application.feature.login.LoginRoute
import com.example.cpen321application.feature.matchmaking.MatchmakingRoute
import com.example.cpen321application.feature.profile.ProfileRoute

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Profile : Screen("profile")
    object Matchmaking : Screen("matchmaking")
    object Leaderboard : Screen("leaderboard")
    object ScheduledGame : Screen("scheduled_game")
    object CreateGame : Screen("create_game")
    object GameDetail : Screen("game/{gameId}") {
        fun createRoute(gameId: String) = "game/$gameId"
    }
    object CreateTournament : Screen("create_tournament")
    object TournamentDetail : Screen("tournament/{tournamentId}") {
        fun createRoute(tournamentId: String) = "tournament/$tournamentId"
    }
}

data class NavGraphArgs(
    val serverClientId: String,
    val session: AuthSession,
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
    val session = args.session
    // Read when needed, not captured, so a refreshed session is always the one used
    val currentUsername = { session.user?.username }

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
            MatchmakingRoute(
                session = args.session,
                onGoToGames = { navController.navigateToTab(Screen.ScheduledGame.route) }
            )
        }
        composable(Screen.Leaderboard.route) {
            LeaderboardRoute(authViewModel = args.authViewModel)
        }
        gamesDestinations(navController, session, currentUsername)
    }
}
