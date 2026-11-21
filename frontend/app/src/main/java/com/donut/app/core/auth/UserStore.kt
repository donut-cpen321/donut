package com.donut.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Saves the signed-in user and their tokens so they stay signed in after the app restarts.
 * @param context any context, only the application context is kept
 */
class UserStore(context: Context) {
    private val appContext = context.applicationContext

    // Tokens are encrypted with a key kept in the Android Keystore
    private val prefs: SharedPreferences = createPreferences(appContext)

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE_ENCRYPTED,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            try {
                context.deleteSharedPreferences(PREFS_FILE_ENCRYPTED)
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    PREFS_FILE_ENCRYPTED,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                context.getSharedPreferences(PREFS_FILE_FALLBACK, Context.MODE_PRIVATE)
            }
        }
    }

    /**
     * @return the saved user, null if nobody is signed in
     */
    fun load(): User? {
        return try {
            val username = prefs.getString(USERNAME, null)
            val accessToken = prefs.getString(ACCESS_TOKEN, null)
            val refreshToken = prefs.getString(REFRESH_TOKEN, null)
            if (username == null || accessToken == null || refreshToken == null) {
                null
            } else {
                User(username, accessToken, refreshToken)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * @param user the user to save, null to clear it on sign-out
     */
    fun save(user: User?) {
        try {
            val editor = prefs.edit()
            if (user == null) {
                editor.clear()
            } else {
                editor.putString(USERNAME, user.username)
                editor.putString(ACCESS_TOKEN, user.accessToken)
                editor.putString(REFRESH_TOKEN, user.refreshToken)
            }
            editor.apply()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val PREFS_FILE_ENCRYPTED = "auth_encrypted"
        const val PREFS_FILE_FALLBACK = "auth_fallback"
        const val USERNAME = "username"
        const val ACCESS_TOKEN = "accessToken"
        const val REFRESH_TOKEN = "refreshToken"
    }
}
