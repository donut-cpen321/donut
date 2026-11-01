package com.example.cpen321application.feature.profile

data class ProfileUiState(
    val username: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
