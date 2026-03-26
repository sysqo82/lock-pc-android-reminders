package com.locpc.reminders

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

object SecurePrefs {
    private lateinit var encryptedPrefs: SharedPreferences

    const val KEY_JWT = "jwt_token"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_EMAIL = "logged_in_email"
    const val KEY_API_URL = "api_url"
    const val KEY_DEVICE_ID = "device_id"

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(App.instance)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun initialize(context: Context) {
        try {
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "reminders_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Timber.d("SecurePrefs initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing SecurePrefs, using plain SharedPreferences fallback")
            encryptedPrefs = context.getSharedPreferences("reminders_plain", Context.MODE_PRIVATE)
        }
    }

    fun get(): SharedPreferences {
        if (!::encryptedPrefs.isInitialized) {
            throw IllegalStateException("SecurePrefs not initialized. Call initialize() first.")
        }
        return encryptedPrefs
    }

    fun getString(key: String, default: String? = null): String? {
        return get().getString(key, default)
    }

    fun putString(key: String, value: String) {
        get().edit().putString(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int {
        return get().getInt(key, default)
    }

    fun putInt(key: String, value: Int) {
        get().edit().putInt(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return get().getBoolean(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        get().edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        get().edit().remove(key).apply()
    }

    fun clear() {
        get().edit().clear().apply()
        Timber.d("SecurePrefs cleared")
    }
}
