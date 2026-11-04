package com.example.cpen321application.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.cpen321application.core.auth.AuthViewModel

// "game/", the start of every game screen's route
private val GAME_ROUTE_PREFIX = Screen.GameDetail.route.substringBefore("{")

@Composable
fun MainScreen(
    serverClientId: String,
    notificationRoute: String?,
    onNotificationHandled: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Login.route

    val user by authViewModel.user.collectAsState()
    val isSignedIn = user != null

    // Picked once: changing NavHost's start destination would reset the back stack
    val startRoute = rememberSaveable {
        if (isSignedIn) {
            Screen.Profile.route
        } else {
            Screen.Login.route
        }
    }

    // Sign-in, sign-out and a rejected refresh token all change the user, so every auth redirect happens here
    LaunchedEffect(isSignedIn) {
        val target = authRedirect(isSignedIn, navController.currentDestination?.route)
        if (target != null) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // A tapped push notification opens its screen, only for a signed-in user
    LaunchedEffect(notificationRoute) {
        if (notificationRoute != null && isSignedIn) {
            if (notificationRoute.startsWith(GAME_ROUTE_PREFIX)) {
                // A game opens over the Games tab, so back goes to the games list
                navController.navigateToTab(Screen.ScheduledGame.route)
                navController.navigate(notificationRoute)
            } else {
                navController.navigateToTab(notificationRoute)
            }
        }
        if (notificationRoute != null) {
            onNotificationHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(navController = navController)
            }
        },
    ) { innerPadding ->
        val navGraphArgs = NavGraphArgs(
            serverClientId = serverClientId,
            session = authViewModel.session,
            startRoute = startRoute,
            authViewModel = authViewModel,
            onLoginSuccess = { newUser -> authViewModel.signIn(newUser) },
            onSignOut = { authViewModel.logout() },
        )
        AppNavGraph(
            navController = navController,
            args = navGraphArgs,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * Where the user has to go after their sign-in state changed
 * @param isSignedIn whether a user is signed in
 * @param currentRoute the route on screen, null before the first one
 * @return Profile for a signed-in user on Login, Login for a signed-out user anywhere else, null to stay
 */
fun authRedirect(isSignedIn: Boolean, currentRoute: String?): String? {
    // No screen yet: the NavHost hasn't set its graph, and its start route already matches the user
    if (currentRoute == null) {
        return null
    }
    // Signed in but stuck on Login → go to Profile
    if (isSignedIn && currentRoute == Screen.Login.route) {
        return Screen.Profile.route
    }
    // Signed out but on some other screen → go to Login
    if (!isSignedIn && currentRoute != Screen.Login.route) {
        return Screen.Login.route
    }
    return null
}
