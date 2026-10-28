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

@Composable
fun MainScreen(
    serverClientId: String,
    authViewModel: AuthViewModel = viewModel()
) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Login.route

    val user by authViewModel.user.collectAsState()
    val isSignedIn = user != null

    // Picked once: changing NavHost's start destination would reset the back stack
    val startRoute = rememberSaveable { if (isSignedIn) Screen.Profile.route else Screen.Login.route }

    // Sign-in, sign-out and a rejected refresh token all change the user, so every auth redirect happens here
    LaunchedEffect(isSignedIn) {
        val target = authRedirect(isSignedIn, navController.currentDestination?.route)
        if (target != null) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(navController = navController)
            }
        }
    ) { innerPadding ->
        AppNavGraph(
            navController = navController,
            serverClientId = serverClientId,
            startRoute = startRoute,
            onLoginSuccess = authViewModel::signIn,
            onSignOut = authViewModel::logout,
            modifier = Modifier.padding(innerPadding)
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
