package com.locpc.reminders.api

import android.content.Context
import com.locpc.reminders.SecurePrefs
import com.locpc.reminders.NetworkClient
import timber.log.Timber

object ApiManager {
    private var apiService: ApiService? = null

    fun initialize(context: Context) {
        SecurePrefs.initialize(context)
    }

    fun getApiService(): ApiService {
        return apiService ?: NetworkClient.create(ApiService::class.java).also {
            apiService = it
            Timber.d("ApiService initialized")
        }
    }

    fun isLoggedIn(): Boolean {
        return SecurePrefs.getBoolean(SecurePrefs.KEY_IS_LOGGED_IN, false)
    }

    fun setLoggedIn(email: String) {
        SecurePrefs.putString(SecurePrefs.KEY_EMAIL, email)
        SecurePrefs.putBoolean(SecurePrefs.KEY_IS_LOGGED_IN, true)
        Timber.d("User logged in: $email")
    }

    fun getLoggedInEmail(): String? {
        return SecurePrefs.getString(SecurePrefs.KEY_EMAIL)
    }

    suspend fun logout() {
        try {
            val apiService = getApiService()
            val response = apiService.logout()
            if (response.isSuccessful) {
                Timber.d("Logout API call successful")
            } else {
                Timber.w("Logout API returned ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calling logout API")
        }

        // Clear local state
        SecurePrefs.putBoolean(SecurePrefs.KEY_IS_LOGGED_IN, false)
        SecurePrefs.remove(SecurePrefs.KEY_EMAIL)
        NetworkClient.clearCookies()
        apiService = null
        Timber.d("Logged out and cleared credentials")
    }

    fun clearSession() {
        // Clear local session without calling API (for when session is invalid)
        SecurePrefs.putBoolean(SecurePrefs.KEY_IS_LOGGED_IN, false)
        SecurePrefs.remove(SecurePrefs.KEY_EMAIL)
        NetworkClient.clearCookies()
        apiService = null
        Timber.d("Cleared invalid session")
    }
}
