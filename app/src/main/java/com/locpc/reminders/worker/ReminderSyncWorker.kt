package com.locpc.reminders.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.locpc.reminders.api.ApiManager
import com.locpc.reminders.data.Reminder
import com.locpc.reminders.util.NotificationHelper
import timber.log.Timber

class ReminderSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!ApiManager.isLoggedIn()) return Result.success()

            val apiService = ApiManager.getApiService()
            val response = apiService.getReminders()

            if (response.isSuccessful) {
                val reminders = response.body() ?: emptyList()
                val prefs = applicationContext.getSharedPreferences("locpc_reminders", Context.MODE_PRIVATE)
                val notificationHelper = NotificationHelper(applicationContext)

                // Cancel alarms for any reminders that were removed since last sync
                val oldJson = prefs.getString("reminders_list", null)
                if (!oldJson.isNullOrBlank()) {
                    try {
                        val type = object : TypeToken<List<Reminder>>() {}.type
                        val oldReminders: List<Reminder> = Gson().fromJson(oldJson, type)
                        val updatedIds = reminders.map { it.id }.toSet()
                        val editor = prefs.edit()
                        for (old in oldReminders) {
                            if (old.id !in updatedIds) {
                                notificationHelper.cancelReminderAlarms(old.id)
                                editor.remove("notified_${old.id}")
                                Timber.d("ReminderSyncWorker: cancelled alarms for removed reminder: ${old.id}")
                            }
                        }
                        editor.apply()
                    } catch (e: Exception) {
                        Timber.e(e, "ReminderSyncWorker: failed to diff old reminders")
                    }
                }

                // Reschedule alarms from latest server data
                for (reminder in reminders) {
                    notificationHelper.scheduleReminder(reminder)
                }

                prefs.edit()
                    .putString("reminders_list", Gson().toJson(reminders))
                    .putLong("last_sync", System.currentTimeMillis())
                    .apply()

                Timber.d("ReminderSyncWorker: scheduled alarms for ${reminders.size} reminders")
                Result.success()
            } else if (response.code() == 401) {
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "ReminderSyncWorker error")
            Result.retry()
        }
    }
}
