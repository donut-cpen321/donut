package com.donut.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.AuthViewModel
import com.donut.app.core.auth.User
import com.donut.app.core.data.repository.FriendsRepositoryImpl
import com.donut.app.core.data.repository.GamesRepositoryImpl
import com.donut.app.core.data.repository.StatsRepositoryImpl
import com.donut.app.feature.leaderboard.LeaderboardRoute
import com.donut.app.feature.login.LoginRoute
import com.donut.app.feature.matchmaking.MatchmakingRoute
import com.donut.app.feature.matchmaking.MatchmakingViewModel
import com.donut.app.feature.matchsummary.MatchSummaryRoute
import com.donut.app.feature.matchsummary.MatchSummaryViewModel
import com.donut.app.feature.playerstats.PlayerStatsRoute
import com.donut.app.feature.playerstats.PlayerStatsViewModel
import com.donut.app.feature.profile.ProfileRoute

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Profile : Screen("profile")
    object Matchmaking : Screen("matchmaking")
    object Leaderboard : Screen("leaderboard")
    object PlayerStats : Screen("stats")
    object ScheduledGame : Screen("scheduled_game")
    object CreateGame : Screen("create_game")
    object GameDetail : Screen("game/{gameId}") {
        fun createRoute(gameId: String) = "game/$gameId"
    }
    object MatchSummary : Screen("match/{gameId}") {
        fun createRoute(gameId: String) = "match/$gameId"
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
    val matchmakingViewModel: MatchmakingViewModel,
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
                onOpenMatch = { gameId -> navController.navigate(Screen.MatchSummary.createRoute(gameId)) },
                onOpenStats = { navController.navigate(Screen.PlayerStats.route) },
                onOpenGame = { gameId -> navController.navigate(Screen.GameDetail.createRoute(gameId)) },
            )
        }
        playerStatsDestination(navController, session)
        composable(
            route = Screen.MatchSummary.route,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId").orEmpty()
            MatchSummaryRoute(
                viewModel = viewModel {
                    MatchSummaryViewModel(
                        gameId = gameId,
                        gamesRepository = GamesRepositoryImpl(session),
                        friendsRepository = FriendsRepositoryImpl(),
                        session = session,
                        currentUsername = currentUsername,
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Matchmaking.route) {
            MatchmakingRoute(viewModel = args.matchmakingViewModel)
        }
        composable(Screen.Leaderboard.route) {
            LeaderboardRoute(authViewModel = args.authViewModel)
        }
        gamesDestinations(navController, session, currentUsername)
    }
}

private fun NavGraphBuilder.playerStatsDestination(navController: NavHostController, session: AuthSession) {
    composable(Screen.PlayerStats.route) {
        PlayerStatsRoute(
            viewModel = viewModel { PlayerStatsViewModel(StatsRepositoryImpl(session)) },
            onBack = { navController.popBackStack() },
        )
    }
}
