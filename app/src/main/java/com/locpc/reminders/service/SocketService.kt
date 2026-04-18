package com.locpc.reminders.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.locpc.reminders.App
import com.locpc.reminders.ApiConfig
import com.locpc.reminders.NetworkClient
import com.locpc.reminders.R
import com.locpc.reminders.SocketManager
import com.locpc.reminders.api.ApiManager
import com.locpc.reminders.util.LocationHelper
import com.locpc.reminders.worker.LocationWorker
import timber.log.Timber

/**
 * Persistent foreground service that keeps the Socket.IO connection alive while the app is in
 * the background or the [com.locpc.reminders.MainActivity] has been destroyed.
 *
 * When the server emits a `locate_device` event (e.g. triggered by the admin pressing the
 * location button), this service responds by enqueueing an expedited [LocationWorker] via
 * WorkManager.  Using WorkManager avoids the Android 12+ restriction that prevents calling
 * [Context.startForegroundService] from a background context.
 *
 * This service uses the `connectedDevice` foreground-service type (Android 14+), which has no
 * daily time limit and is appropriate for maintaining a persistent network connection to a
 * remote device/server.
 */
class SocketService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 5

        fun start(context: Context) {
            val intent = Intent(context, SocketService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("SocketService: onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())

        if (ApiManager.isLoggedIn()) {
            val deviceId = LocationHelper(this).getDeviceId()
            val cookie = NetworkClient.getCookieHeader(ApiConfig.BASE_URL)

            // Only set the locate_device callback — MainActivity still owns
            // onRemindersUpdated and onForceLogout.
            SocketManager.onLocateDevice = { enqueueLocationWork() }
            SocketManager.connect(cookie, deviceId)
        }

        return START_STICKY
    }

    private fun enqueueLocationWork() {
        Timber.d("SocketService: locate_device received, enqueueing LocationWorker")
        val request = OneTimeWorkRequestBuilder<LocationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork("location_update", ExistingWorkPolicy.REPLACE, request)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, App.CHANNEL_LOCATION)
            .setContentTitle("LockPC Active")
            .setContentText("Monitoring reminders and location")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("SocketService: destroyed")
    }
}
