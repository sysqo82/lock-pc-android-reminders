package com.locpc.reminders.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.locpc.reminders.api.ApiManager
import com.locpc.reminders.data.Reminder
import com.locpc.reminders.service.SocketService
import com.locpc.reminders.util.GeofenceManager
import com.locpc.reminders.util.NotificationHelper
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Timber.d("BootReceiver: device booted, rescheduling reminder alarms")

            val prefs = context.getSharedPreferences("locpc_reminders", Context.MODE_PRIVATE)
            val json = prefs.getString("reminders_list", null)

            if (json != null) {
                try {
                    val type = object : TypeToken<List<Reminder>>() {}.type
                    val reminders: List<Reminder> = Gson().fromJson(json, type)
                    val helper = NotificationHelper(context)
                    for (reminder in reminders) {
                        helper.scheduleReminder(reminder)
                    }
                    Timber.d("BootReceiver: rescheduled ${reminders.size} reminder alarms")
                } catch (e: Exception) {
                    Timber.e(e, "BootReceiver: error rescheduling alarms")
                }
            }

            // Re-register geofences from local cache so they are active before the
            // network is fully available. SocketService will refresh from the server
            // and call registerZones again once connected.
            val persistedZones = GeofenceManager.loadPersistedZones(context)
            if (persistedZones.isNotEmpty()) {
                Timber.d("BootReceiver: re-registering ${persistedZones.size} persisted geofence(s)")
                GeofenceManager.registerZones(context, persistedZones)
            }

            if (ApiManager.isLoggedIn()) {
                Timber.d("BootReceiver: user is logged in, starting SocketService")
                SocketService.start(context)
            }
        }
    }
}

