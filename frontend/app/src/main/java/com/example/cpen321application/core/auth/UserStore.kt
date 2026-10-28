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
        val username = prefs.getString(USERNAME, null)
        val accessToken = prefs.getString(ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(REFRESH_TOKEN, null)
        if (username == null || accessToken == null || refreshToken == null) {
            return null
        }
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
            editor.putString(ACCESS_TOKEN, user.accessToken)
            editor.putString(REFRESH_TOKEN, user.refreshToken)
        }
        editor.apply()
    }

    // Key names in the file, like Java's `private static final`
    private companion object {
        const val USERNAME = "username"
        const val ACCESS_TOKEN = "accessToken"
        const val REFRESH_TOKEN = "refreshToken"
    }
}
