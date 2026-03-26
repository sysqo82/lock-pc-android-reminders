package com.locpc.reminders

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize secure preferences
        SecurePrefs.initialize(this)

        // Create notification channels
        createNotificationChannels()

        Timber.d("Application initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Reminders notification channel
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for task reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(500, 250, 500)
                enableLights(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(remindersChannel)

            // Location service channel
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location service"
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(locationChannel)
        }
    }

    companion object {
        lateinit var instance: App
            private set

        const val CHANNEL_REMINDERS = "reminders_channel"
        const val CHANNEL_LOCATION = "location_channel"
    }
}
