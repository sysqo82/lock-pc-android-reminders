package com.locpc.reminders.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.locpc.reminders.api.ApiManager
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
