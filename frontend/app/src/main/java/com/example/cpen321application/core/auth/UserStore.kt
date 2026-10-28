package com.example.cpen321application.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Saves the signed-in user and their tokens so they stay signed in after the app restarts.
 * @param context any context, only the application context is kept
 */
class UserStore(context: Context) {
    // Tokens are encrypted with a key kept in the Android Keystore
    private val prefs = EncryptedSharedPreferences.create(
        "auth_encrypted",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * @return the saved user, null if nobody is signed in
     */
    fun load(): User? {
        val username = prefs.getString(USERNAME, null) ?: return null
        val accessToken = prefs.getString(ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(REFRESH_TOKEN, null) ?: return null
        return User(username, accessToken, refreshToken)
    }

    /**
     * @param user the user to save, null to clear it on sign-out
     */
    fun save(user: User?) {
        val editor = prefs.edit()
        if (user == null) {
            editor.clear()
        } else {
            editor.putString(USERNAME, user.username)
                .putString(ACCESS_TOKEN, user.accessToken)
                .putString(REFRESH_TOKEN, user.refreshToken)
        }
        editor.apply()
    }

    private companion object {
        const val USERNAME = "username"
        const val ACCESS_TOKEN = "accessToken"
        const val REFRESH_TOKEN = "refreshToken"
    }
}
