package com.locpc.reminders.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.app.NotificationCompat
import com.locpc.reminders.App
import com.locpc.reminders.MainActivity
import com.locpc.reminders.R
import com.locpc.reminders.ReminderAlertActivity
import com.locpc.reminders.data.Reminder
import com.locpc.reminders.receiver.AlarmReceiver
import timber.log.Timber
import java.util.Calendar

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ---------- Alarm scheduling ----------

    fun scheduleReminder(reminder: Reminder) {
        val time = reminder.time ?: return
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return

        val days = parseDays(reminder.getDaysString())
        if (days.isEmpty()) {
            scheduleAlarmForDay(reminder, -1, hour, minute)
        } else {
            for (dayOfWeek in days) {
                scheduleAlarmForDay(reminder, dayOfWeek, hour, minute)
            }
        }
    }

    /** Cancel every alarm for every reminder stored locally — call this on logout. */
    fun cancelAllAlarms() {
        try {
            val json = context.getSharedPreferences("locpc_reminders", Context.MODE_PRIVATE)
                .getString("reminders_list", null) ?: return
            val type = object : com.google.gson.reflect.TypeToken<List<Reminder>>() {}.type
            val reminders: List<Reminder> = com.google.gson.Gson().fromJson(json, type)
            for (reminder in reminders) {
                cancelReminderAlarms(reminder.id)
            }
            Timber.d("cancelAllAlarms: cancelled alarms for ${reminders.size} reminders")
        } catch (e: Exception) {
            Timber.e(e, "cancelAllAlarms failed")
        }
    }

    fun cancelReminderAlarms(reminderId: String) {
        for (slot in listOf(-1, 1, 2, 3, 4, 5, 6, 7)) {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE_ALARM
            }
            val pi = PendingIntent.getBroadcast(
                context,
                "${reminderId}_${slot}".hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun scheduleAlarmForDay(reminder: Reminder, dayOfWeek: Int, hour: Int, minute: Int) {
        val title = reminder.description ?: reminder.title
        val description = if (reminder.description != null) reminder.title else null
        val triggerAt = nextOccurrence(dayOfWeek, hour, minute)
        scheduleActivityAlarm(reminder.id, title, description, dayOfWeek, hour, minute, triggerAt)
        val when_ = java.text.SimpleDateFormat("EEE dd MMM HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(triggerAt))
        Timber.d("Scheduled alarm for '${reminder.title}' at $when_")
    }

    /** Called from ReminderAlertActivity after it fires to schedule the next occurrence. */
    fun rescheduleReminder(reminderId: String, title: String, description: String?, dayOfWeek: Int, hour: Int, minute: Int) {
        val triggerAt = nextOccurrence(dayOfWeek, hour, minute)
        scheduleActivityAlarm(reminderId, title, description, dayOfWeek, hour, minute, triggerAt)
        Timber.d("Rescheduled alarm for '$title' at ${java.util.Date(triggerAt)}")
    }

    /**
     * Core scheduler: setAlarmClock fires a BroadcastReceiver, which posts a notification
     * with setFullScreenIntent. The notification system (system_server) then wakes the screen
     * and shows ReminderAlertActivity — this is the correct pattern for screen-off alarms on
     * Android 16 and bypasses all Background Activity Launch restrictions.
     */
    private fun scheduleActivityAlarm(
        reminderId: String, title: String, description: String?,
        dayOfWeek: Int, hour: Int, minute: Int, triggerAt: Long
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val msg = "ALARM NOT SET: exact alarm permission denied. Go to Settings > Apps > LockPC Reminders > Alarms & Reminders and enable it."
                Timber.e(msg)
                postToast(context, msg)
                return
            }
        }

        val rc = "${reminderId}_${dayOfWeek}".hashCode()
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_DESCRIPTION, description)
            putExtra(AlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
            putExtra(AlarmReceiver.EXTRA_HOUR, hour)
            putExtra(AlarmReceiver.EXTRA_MINUTE, minute)
        }
        // Always call setAlarmClock() — FLAG_UPDATE_CURRENT replaces the existing alarm's
        // trigger time, which is required when the user edits a reminder's time.
        val alarmPi = PendingIntent.getBroadcast(
            context, rc,
            alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = PendingIntent.getActivity(
            context, rc + 1_000_000,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), alarmPi)
            val when_ = java.text.SimpleDateFormat("EEE dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(triggerAt))
            Timber.d("Alarm set: \"$title\" @ $when_")
        } catch (e: SecurityException) {
            val msg = "ALARM FAILED (SecurityException): ${e.message}"
            Timber.e(e, msg)
            postToast(context, msg)
        } catch (e: Exception) {
            val msg = "ALARM FAILED: ${e.message}"
            Timber.e(e, msg)
            postToast(context, msg)
        }
    }

    companion object {
        private fun postToast(context: Context, msg: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun nextOccurrence(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (dayOfWeek == -1) {
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        } else {
            cal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun parseDays(dayString: String?): List<Int> {
        if (dayString.isNullOrBlank()) return emptyList()
        val map = mapOf(
            "mon" to Calendar.MONDAY, "monday" to Calendar.MONDAY,
            "tue" to Calendar.TUESDAY, "tuesday" to Calendar.TUESDAY,
            "wed" to Calendar.WEDNESDAY, "wednesday" to Calendar.WEDNESDAY,
            "thu" to Calendar.THURSDAY, "thursday" to Calendar.THURSDAY,
            "fri" to Calendar.FRIDAY, "friday" to Calendar.FRIDAY,
            "sat" to Calendar.SATURDAY, "saturday" to Calendar.SATURDAY,
            "sun" to Calendar.SUNDAY, "sunday" to Calendar.SUNDAY
        )
        return dayString.split(",").mapNotNull { map[it.trim().lowercase()] }
    }

    // ---------- Show notifications ----------

    /**
     * Builds the alert Notification object. Used by ReminderAlertService.startForeground()
     * so the notification fires reliably from the background.
     */
    fun buildAlertNotification(reminderId: String, title: String, description: String?): android.app.Notification {
        val alertIntent = Intent(context, com.locpc.reminders.ReminderAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("reminder_description", description)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, reminderId.hashCode() + 1, alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context, reminderId.hashCode() + 2, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, App.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createClockIcon())
            .setColor(0xFF007BFF.toInt())
            .setContentTitle(title)
            .setContentText(description ?: "Time for your reminder")
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(500, 250, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(R.drawable.ic_notification, "Dismiss", dismissPi)
            .build()
    }

    /** Called directly by the Test button — posts the notification immediately. */
    fun showNotificationDirect(reminderId: String, title: String, description: String?) {
        notificationManager.notify(reminderId.hashCode(), buildAlertNotification(reminderId, title, description))
    }

    /** Legacy method kept for compatibility — now delegates to showNotificationDirect. */
    fun showReminderNotification(reminder: Reminder) {
        showNotificationDirect(
            reminder.id,
            reminder.description ?: reminder.title,
            if (reminder.description != null) reminder.title else null
        )
    }

    private fun createClockIcon(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw blue background
        paint.color = 0xFF007BFF.toInt()
        canvas.drawCircle((size / 2).toFloat(), (size / 2).toFloat(), (size / 2).toFloat(), paint)

        // Draw white clock outline
        paint.color = -1 // white
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawCircle((size / 2).toFloat(), (size / 2).toFloat(), (size / 2 - 16).toFloat(), paint)

        // Draw clock hands
        paint.strokeWidth = 6f
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 3f
        canvas.drawLine(centerX, centerY, centerX, centerY - radius, paint)
        canvas.drawLine(centerX, centerY, centerX + radius / 1.5f, centerY, paint)

        return bitmap
    }

    fun cancelReminderNotification(reminderId: String) {
        notificationManager.cancel(reminderId.hashCode())
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
