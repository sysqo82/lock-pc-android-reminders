package com.locpc.reminders.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.locpc.reminders.App
import com.locpc.reminders.service.ReminderAlertService
import com.locpc.reminders.util.NotificationHelper
import timber.log.Timber

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE_ALARM -> handleFireAlarm(context, intent)
            ACTION_DISMISS    -> handleDismiss(context, intent)
        }
    }

    private fun handleFireAlarm(context: Context, intent: Intent) {
        val reminderId  = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val title       = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val dayOfWeek   = intent.getIntExtra(EXTRA_DAY_OF_WEEK, -1)
        val hour        = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute      = intent.getIntExtra(EXTRA_MINUTE, -1)

        Timber.d("AlarmReceiver: firing alarm for '$title'")

        // Defensive channel re-creation in case App.onCreate() was not called first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(App.CHANNEL_REMINDERS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(App.CHANNEL_REMINDERS, "Task Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                        enableVibration(true)
                        setBypassDnd(true)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }

        // Path 1: start the activity directly here, while the setAlarmClock BAL exemption window
        // is guaranteed active (we are inside onReceive()). This is the most reliable path on all
        // Android 13+ devices.
        try {
            context.startActivity(
                Intent(context, com.locpc.reminders.ReminderAlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("reminder_id", reminderId)
                    putExtra("reminder_title", title)
                    putExtra("reminder_description", description)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "AlarmReceiver: direct startActivity failed, falling back to service")
        }

        // Path 2: start the foreground service. It re-attempts startActivity() after calling
        // startForeground() (foreground-service BAL exemption) AND posts the persistent notification.
        try {
            context.startForegroundService(
                ReminderAlertService.buildIntent(context, reminderId, title, description)
            )
        } catch (e: Exception) {
            Timber.e(e, "AlarmReceiver: failed to start ReminderAlertService")
        }

        // Reschedule next weekly occurrence
        if (hour >= 0 && minute >= 0) {
            try {
                NotificationHelper(context).rescheduleReminder(reminderId, title, description, dayOfWeek, hour, minute)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reschedule reminder")
            }
        }
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(reminderId.hashCode())
    }

    companion object {
        const val ACTION_FIRE_ALARM  = "com.locpc.reminders.ACTION_FIRE_ALARM"
        const val ACTION_DISMISS     = "com.locpc.reminders.ACTION_DISMISS_REMINDER"
        const val EXTRA_REMINDER_ID  = "reminder_id"
        const val EXTRA_TITLE        = "reminder_title"
        const val EXTRA_DESCRIPTION  = "reminder_description"
        const val EXTRA_DAY_OF_WEEK  = "reminder_day_of_week"
        const val EXTRA_HOUR         = "reminder_hour"
        const val EXTRA_MINUTE       = "reminder_minute"

        fun requestCode(reminderId: String, dayOfWeek: Int): Int =
            "${reminderId}_${dayOfWeek}".hashCode()
    }
}
