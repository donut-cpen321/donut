package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.auth.AuthViewModel
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.data.repository.CourtsRepositoryImpl
import com.example.cpen321application.core.data.repository.GamesRepositoryImpl
import com.example.cpen321application.feature.creategame.CreateGameRoute
import com.example.cpen321application.feature.creategame.CreateGameViewModel
import com.example.cpen321application.feature.gamedetail.GameDetailRoute
import com.example.cpen321application.feature.gamedetail.GameDetailViewModel
import com.example.cpen321application.feature.leaderboard.LeaderboardRoute
import com.example.cpen321application.feature.login.LoginRoute
import com.example.cpen321application.feature.matchmaking.MatchmakingRoute
import com.example.cpen321application.feature.profile.ProfileRoute
import com.example.cpen321application.feature.scheduledgame.ScheduledGameRoute
import com.example.cpen321application.feature.scheduledgame.ScheduledGameViewModel

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
        composable(Screen.ScheduledGame.route) {
            ScheduledGameRoute(
                viewModel = viewModel { ScheduledGameViewModel(GamesRepositoryImpl(session), currentUsername) },
                onGameClick = { gameId -> navController.navigate(Screen.GameDetail.createRoute(gameId)) },
                onCreateGame = { navController.navigate(Screen.CreateGame.route) }
            )
        }
        composable(Screen.CreateGame.route) {
            CreateGameRoute(
                viewModel = viewModel {
                    CreateGameViewModel(GamesRepositoryImpl(session), CourtsRepositoryImpl(session))
                },
                onBack = { navController.popBackStack() },
                onCreated = { gameId ->
                    // Back from the new game goes to the games list, not the empty form
                    navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                        popUpTo(Screen.CreateGame.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId").orEmpty()
            GameDetailRoute(
                viewModel = viewModel {
                    GameDetailViewModel(
                        gameId = gameId,
                        gamesRepository = GamesRepositoryImpl(session),
                        courtsRepository = CourtsRepositoryImpl(session),
                        currentUsername = currentUsername
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
