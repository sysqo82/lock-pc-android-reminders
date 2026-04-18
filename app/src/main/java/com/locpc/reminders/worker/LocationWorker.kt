package com.locpc.reminders.worker

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.locpc.reminders.App
import com.locpc.reminders.R
import com.locpc.reminders.api.ApiManager
import com.locpc.reminders.data.LocationUpdate
import com.locpc.reminders.util.LocationHelper
import timber.log.Timber

/**
 * WorkManager CoroutineWorker that fetches the current device location and uploads it to the
 * server.  Using WorkManager (with setExpedited) instead of a raw foreground-service start avoids
 * the Android 12+ [android.app.ForegroundServiceStartNotAllowedException] that is thrown when
 * [Context.startForegroundService] is called while the app is in the background.
 */
class LocationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val NOTIFICATION_ID = 4
    }

    override suspend fun doWork(): Result {
        val locationHelper = LocationHelper(applicationContext)

        if (!locationHelper.hasLocationPermission()) {
            Timber.w("LocationWorker: location permission not granted")
            return Result.failure()
        }

        val location = locationHelper.getCurrentLocation() ?: run {
            Timber.w("LocationWorker: could not obtain location")
            return Result.retry()
        }

        val update = LocationUpdate(
            latitude = location.first,
            longitude = location.second,
            accuracy = location.third,
            deviceId = locationHelper.getDeviceId(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )

        return try {
            val response = ApiManager.getApiService().updateLocation(update)
            if (response.isSuccessful) {
                Timber.d("LocationWorker: location uploaded successfully")
                Result.success()
            } else {
                Timber.w("LocationWorker: upload failed with code ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "LocationWorker: error uploading location")
            Result.retry()
        }
    }

    /** Required by WorkManager when the work runs as expedited on Android 12+. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, App.CHANNEL_LOCATION)
                .setContentTitle("Updating location")
                .setContentText("Getting device location…")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build()
        )
    }
}
