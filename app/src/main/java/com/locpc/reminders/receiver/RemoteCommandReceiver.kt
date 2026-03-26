package com.locpc.reminders.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.locpc.reminders.service.LocationService
import com.locpc.reminders.service.ReminderSyncService
import timber.log.Timber

class RemoteCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_LOCATE_DEVICE -> {
                Timber.d("RemoteCommandReceiver: Locate device command received")
                if (context != null) {
                    val locationIntent = Intent(context, LocationService::class.java)
                    context.startForegroundService(locationIntent)
                }
            }

            ACTION_SYNC_REMINDERS -> {
                Timber.d("RemoteCommandReceiver: Sync reminders command received")
                if (context != null) {
                    val syncIntent = Intent(context, ReminderSyncService::class.java)
                    JobIntentService.enqueueWork(
                        context,
                        ReminderSyncService::class.java,
                        ReminderSyncService.JOB_ID,
                        syncIntent
                    )
                }
            }

            else -> {
                Timber.w("RemoteCommandReceiver: Unknown action ${intent?.action}")
            }
        }
    }

    companion object {
        const val ACTION_LOCATE_DEVICE = "com.locpc.reminders.LOCATE_DEVICE"
        const val ACTION_SYNC_REMINDERS = "com.locpc.reminders.SYNC_REMINDERS"
    }
}
