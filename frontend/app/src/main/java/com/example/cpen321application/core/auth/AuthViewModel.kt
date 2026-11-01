package com.example.cpen321application.core.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the app's AuthSession so it survives screen rotation.
 * The user is saved on every sign-in, refresh and sign-out, so they stay signed in after the app restarts.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val userStore = UserStore(application)

    // The signed-in user, starting with the one saved from the last run (null if signed out).
    // _user is changed here; screens watch the read-only `user` and redraw when it changes.
    private val _user = MutableStateFlow(userStore.load())
    val user: StateFlow<User?> = _user.asStateFlow()

    val session = AuthSession(
        api = RetrofitClient.apiService,
        initialUser = _user.value,
        onUserChanged = { newUser ->
            userStore.save(newUser)
            _user.value = newUser
        }
    )

    /**
     * Starts a session with the user returned by the backend login
     */
    fun signIn(newUser: User) {
        session.signIn(newUser)
    }

    /**
     * Signs out on the backend and clears the Google credential so the account picker shows next time
     */
    fun logout() {
        viewModelScope.launch {
            session.logout()
            signOut(getApplication())
        }
    }
}
