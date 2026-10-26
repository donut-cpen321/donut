package com.example.cpen321application.feature.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.auth.signInWithGoogle
import com.example.cpen321application.core.data.repository.AuthRepository
import com.example.cpen321application.core.data.repository.AuthRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signIn(
        context: Context,
        serverClientId: String,
        onSuccess: (User) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val token = signInWithGoogle(context, serverClientId)
            if (token != null) {
                val result = authRepository.loginWithBackend(token)
                result.onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(user)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading =false,
                            errorMessage = error.localizedMessage ?: "Backend authentication failed."
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Google Sign-In failed or was cancelled."
                    )
                }
            }
        }
    }
}
