package com.locpc.reminders.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.locpc.reminders.App
import com.locpc.reminders.R
import com.locpc.reminders.api.ApiManager
import com.locpc.reminders.data.LocationUpdate
import com.locpc.reminders.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class LocationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val locationHelper by lazy { LocationHelper(this) }

    companion object {
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("LocationService: onStartCommand called")

        // Post foreground notification
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        // Start tracking location
        startLocationTracking()

        return START_STICKY
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, App.CHANNEL_LOCATION)
            .setContentTitle("App is running")
            .setContentText("Keeping your reminders up to date")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun startLocationTracking() {
        scope.launch {
            try {
                if (locationHelper.hasLocationPermission()) {
                    val location = locationHelper.getCurrentLocation()
                    if (location != null) {
                        val deviceId = locationHelper.getDeviceId()
                        val locationUpdate = LocationUpdate(
                            latitude = location.first,
                            longitude = location.second,
                            accuracy = 0f,
                            deviceId = deviceId
                        )
                        try {
                            val apiService = ApiManager.getApiService()
                            val response = apiService.updateLocation(locationUpdate)
                            if (response.isSuccessful) {
                                Timber.d("Location updated successfully: ${location.first}, ${location.second}")
                            } else {
                                Timber.w("Failed to update location: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error sending location to server")
                        }
                    } else {
                        Timber.d("Could not get current location")
                    }
                } else {
                    Timber.w("Location permission not granted")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in location tracking")
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("LocationService: onDestroy called")
        scope.launch {
            // Clean shutdown
        }
    }
}
