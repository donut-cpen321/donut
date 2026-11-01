package com.example.cpen321application.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRedirectTest {
    // Input: user just signed in, still on the login screen
    // Expected output: Profile
    @Test
    fun sendsSignedInUserFromLoginToProfile() {
        assertEquals(Screen.Profile.route, authRedirect(isSignedIn = true, currentRoute = Screen.Login.route))
    }

    // Input: user signed out (or their refresh token was rejected) on a signed-in screen
    // Expected output: Login
    @Test
    fun sendsSignedOutUserToLogin() {
        assertEquals(Screen.Login.route, authRedirect(isSignedIn = false, currentRoute = Screen.Profile.route))
        assertEquals(Screen.Login.route, authRedirect(isSignedIn = false, currentRoute = Screen.Matchmaking.route))
    }

    // Input: signed-in user on a signed-in screen, or signed-out user on Login
    // Expected output: null, no navigation
    @Test
    fun staysWhenRouteMatchesState() {
        assertNull(authRedirect(isSignedIn = true, currentRoute = Screen.Profile.route))
        assertNull(authRedirect(isSignedIn = false, currentRoute = Screen.Login.route))
    }
}
