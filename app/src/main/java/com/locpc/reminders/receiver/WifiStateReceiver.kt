package com.locpc.reminders.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.locpc.reminders.App
import com.locpc.reminders.R
import com.locpc.reminders.WifiAlertActivity
import timber.log.Timber

/**
 * Static manifest receiver for Wi-Fi state changes.
 * Guaranteed to fire even when the app process is dead (WIFI_STATE_CHANGED is exempt from
 * Android 8+ implicit broadcast restrictions).
 *
 * Instead of recalculating GPS position (unreliable when woken cold), this reads the
 * "inside zones" set maintained by [GeofenceBroadcastReceiver] — if the device entered a
 * zone, that zone name was persisted; if it exited, it was removed.
 */
class WifiStateReceiver : BroadcastReceiver() {

    companion object {
        const val NOTIFICATION_ID = 9001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)

        if (state == WifiManager.WIFI_STATE_ENABLED) {
            // Wi-Fi turned back on — dismiss any lingering alert notification
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }

        if (state != WifiManager.WIFI_STATE_DISABLED) return

        // Read which zones the device is currently inside (set by GeofenceBroadcastReceiver)
        val insideZones = GeofenceBroadcastReceiver.getInsideZones(context)
        Timber.d("WifiStateReceiver: Wi-Fi off, currently inside zones: $insideZones")

        if (insideZones.isEmpty()) return

        val zoneName = insideZones.first()
        showWifiAlert(context, zoneName)
    }

    private fun showWifiAlert(context: Context, zoneName: String) {
        val activityIntent = Intent(context, WifiAlertActivity::class.java).apply {
            putExtra(WifiAlertActivity.EXTRA_ZONE_NAME, zoneName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, App.CHANNEL_GEOFENCE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Turn on Wi-Fi")
            .setContentText("You are in \"$zoneName\" — please turn on Wi-Fi")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.e(e, "WifiStateReceiver: notification permission not granted")
        }
    }
}
