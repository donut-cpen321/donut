package com.donut.app.feature.login

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
