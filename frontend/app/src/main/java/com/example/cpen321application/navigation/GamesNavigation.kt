package com.example.cpen321application.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.repository.CourtsRepositoryImpl
import com.example.cpen321application.core.data.repository.FriendsRepositoryImpl
import com.example.cpen321application.core.data.repository.GamesRepositoryImpl
import com.example.cpen321application.core.data.repository.TournamentsRepositoryImpl
import com.example.cpen321application.feature.creategame.CreateGameRoute
import com.example.cpen321application.feature.creategame.CreateGameViewModel
import com.example.cpen321application.feature.createtournament.CreateTournamentRoute
import com.example.cpen321application.feature.createtournament.CreateTournamentViewModel
import com.example.cpen321application.feature.gamedetail.GameDetailRoute
import com.example.cpen321application.feature.gamedetail.GameDetailViewModel
import com.example.cpen321application.feature.scheduledgame.ScheduledGameRoute
import com.example.cpen321application.feature.scheduledgame.ScheduledGameViewModel
import com.example.cpen321application.feature.tournamentdetail.TournamentDetailRoute
import com.example.cpen321application.feature.tournamentdetail.TournamentDetailViewModel

/**
 * The Games tab and the screens it opens: creating a game or a tournament, a game and a tournament's bracket
 * @param navController moves between the screens
 * @param session sends the screens' requests as the signed-in user
 * @param currentUsername the signed-in user's username, null if signed out
 */
fun NavGraphBuilder.gamesDestinations(
    navController: NavHostController,
    session: AuthSession,
    currentUsername: () -> String?
) {
    scheduledGameDestination(navController, session, currentUsername)
    createGameDestination(navController, session)
    gameDetailDestination(navController, session, currentUsername)
    createTournamentDestination(navController, session)
    tournamentDetailDestination(navController, session, currentUsername)
}

private fun NavGraphBuilder.scheduledGameDestination(
    navController: NavHostController,
    session: AuthSession,
    currentUsername: () -> String?
) {
    composable(Screen.ScheduledGame.route) {
        ScheduledGameRoute(
            viewModel = viewModel {
                ScheduledGameViewModel(
                    gamesRepository = GamesRepositoryImpl(session),
                    tournamentsRepository = TournamentsRepositoryImpl(session),
                    currentUsername = currentUsername
                )
            },
            onGameClick = { gameId -> navController.navigate(Screen.GameDetail.createRoute(gameId)) },
            onTournamentClick = { tournamentId ->
                navController.navigate(Screen.TournamentDetail.createRoute(tournamentId))
            },
            onCreateGame = { navController.navigate(Screen.CreateGame.route) },
            onCreateTournament = { navController.navigate(Screen.CreateTournament.route) }
        )
    }
}

private fun NavGraphBuilder.createGameDestination(navController: NavHostController, session: AuthSession) {
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
}

private fun NavGraphBuilder.gameDetailDestination(
    navController: NavHostController,
    session: AuthSession,
    currentUsername: () -> String?
) {
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
                    currentUsername = currentUsername,
                    friendsRepository = FriendsRepositoryImpl(),
                    session = session
                )
            },
            onBack = { navController.popBackStack() },
            onOpenTournament = { tournamentId ->
                navController.navigate(Screen.TournamentDetail.createRoute(tournamentId))
            }
        )
    }
}

private fun NavGraphBuilder.createTournamentDestination(navController: NavHostController, session: AuthSession) {
    composable(Screen.CreateTournament.route) {
        CreateTournamentRoute(
            viewModel = viewModel {
                CreateTournamentViewModel(TournamentsRepositoryImpl(session), CourtsRepositoryImpl(session))
            },
            onBack = { navController.popBackStack() },
            onCreated = { tournamentId ->
                // Back from the new tournament goes to the games list, not the empty form
                navController.navigate(Screen.TournamentDetail.createRoute(tournamentId)) {
                    popUpTo(Screen.CreateTournament.route) { inclusive = true }
                }
            }
        )
    }
}

private fun NavGraphBuilder.tournamentDetailDestination(
    navController: NavHostController,
    session: AuthSession,
    currentUsername: () -> String?
) {
    composable(
        route = Screen.TournamentDetail.route,
        arguments = listOf(navArgument("tournamentId") { type = NavType.StringType })
    ) { backStackEntry ->
        val tournamentId = backStackEntry.arguments?.getString("tournamentId").orEmpty()
        TournamentDetailRoute(
            viewModel = viewModel {
                TournamentDetailViewModel(
                    tournamentId = tournamentId,
                    tournamentsRepository = TournamentsRepositoryImpl(session),
                    currentUsername = currentUsername
                )
            },
            onBack = { navController.popBackStack() },
            onOpenGame = { gameId -> navController.navigate(Screen.GameDetail.createRoute(gameId)) }
        )
    }
}
