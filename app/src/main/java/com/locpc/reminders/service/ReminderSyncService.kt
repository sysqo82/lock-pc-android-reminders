package com.locpc.reminders.service

import android.content.Intent
import androidx.core.app.JobIntentService
import android.content.SharedPreferences
import com.locpc.reminders.api.ApiManager
import com.locpc.reminders.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class ReminderSyncService : JobIntentService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val JOB_ID = 1001
    }

    override fun onHandleWork(intent: Intent) {
        Timber.d("ReminderSyncService: onHandleWork called")

        scope.launch {
            try {
                val apiService = ApiManager.getApiService()
                val response = apiService.getReminders()

                if (response.isSuccessful) {
                    val reminders = response.body() ?: emptyList()
                    
                    if (reminders.isNotEmpty()) {
                        Timber.d("Successfully fetched ${reminders.size} reminders")

                        val notificationHelper = NotificationHelper(applicationContext)
                        val sharedPreferences = getSharedPreferences(
                            "locpc_reminders",
                            MODE_PRIVATE
                        )

                        // Show notifications for pending reminders
                        for (reminder in reminders) {
                            if (reminder.status == "pending") {
                                val alreadyNotified = sharedPreferences.getBoolean(
                                    "notified_${reminder.id}",
                                    false
                                )
                                if (!alreadyNotified) {
                                    notificationHelper.showReminderNotification(reminder)
                                    sharedPreferences.edit()
                                        .putBoolean("notified_${reminder.id}", true)
                                        .apply()
                                    Timber.d("Notification shown for reminder: ${reminder.title}")
                                }
                            }
                        }

                        // Save reminders to local storage
                        val gson = com.google.gson.Gson()
                        val remindersJson = gson.toJson(reminders)
                        sharedPreferences.edit()
                            .putString("reminders_list", remindersJson)
                            .putLong("last_sync", System.currentTimeMillis())
                            .apply()
                        
                        Timber.d("Reminders saved to local storage")
                    } else {
                        Timber.d("No reminders returned from server")
                    }
                } else {
                    Timber.e("Failed to sync reminders: ${response.code()} ${response.message()}")
                    val errorBody = response.errorBody()?.string()
                    Timber.e("Error response: $errorBody")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error syncing reminders")
            }
        }
    }
}
