package com.locpc.reminders.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.locpc.reminders.ReminderAlertActivity
import com.locpc.reminders.util.NotificationHelper
import timber.log.Timber

/**
 * Foreground service that fires the reminder alert notification.
 * Using a foreground service guarantees the notification is posted and the
 * full-screen intent fires regardless of Doze mode, battery optimisation, or
 * whether the screen is on — unlike posting directly from a BroadcastReceiver.
 */
class ReminderAlertService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getStringExtra(EXTRA_REMINDER_ID)
        if (reminderId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)

        Timber.d("ReminderAlertService: showing alert for '$title'")

        val notification = NotificationHelper(this).buildAlertNotification(reminderId, title, description)

        // startForeground() makes this process "foreground", which grants an unconditional BAL
        // (Background Activity Launch) exemption. This means startActivity() below will ALWAYS
        // succeed — even on Android 13/14 with the screen ON where setFullScreenIntent is
        // suppressed to a HUD. This is the same approach used by Google Clock.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(reminderId.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(reminderId.hashCode(), notification)
        }

        // Launch the full-screen alert activity
        try {
            startActivity(
                Intent(this, ReminderAlertActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("reminder_id", reminderId)
                    putExtra("reminder_title", title)
                    putExtra("reminder_description", description)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "ReminderAlertService: failed to start ReminderAlertActivity")
        }

        // Detach the notification from the service so it persists after the service stops,
        // then stop the service — the alert notification remains visible until dismissed.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf(startId)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_DESCRIPTION = "reminder_description"

        fun buildIntent(context: Context, reminderId: String, title: String, description: String?) =
            Intent(context, ReminderAlertService::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DESCRIPTION, description)
            }
    }
}
