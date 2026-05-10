package com.locpc.reminders.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.locpc.reminders.WifiAlertActivity
import timber.log.Timber

/**
 * Receives geofence transition events from Google Play Services.
 *
 * On ENTER/DWELL: persists the zone name to SharedPreferences so [WifiStateReceiver] knows we
 * are currently inside this zone — even if the app process dies and is later woken by a
 * Wi-Fi state change.  If Wi-Fi is already off at the time of entry, launches the alert directly.
 *
 * On EXIT: removes the zone from the persisted set.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            Timber.w("GeofenceBroadcastReceiver: null GeofencingEvent")
            return
        }

        if (event.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(event.errorCode)
            Timber.e("GeofenceBroadcastReceiver: error — $errorMessage")
            return
        }

        val transition = event.geofenceTransition
        val zoneNames = event.triggeringGeofences?.map { it.requestId } ?: emptyList()
        Timber.d("GeofenceBroadcastReceiver: transition=$transition zones=$zoneNames")

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER,
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                addInsideZones(context, zoneNames)
                val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wm?.isWifiEnabled != true) {
                    val zoneName = zoneNames.firstOrNull() ?: "this area"
                    Timber.d("GeofenceBroadcastReceiver: Wi-Fi off on zone entry, launching alert")
                    context.startActivity(Intent(context, WifiAlertActivity::class.java).apply {
                        putExtra(WifiAlertActivity.EXTRA_ZONE_NAME, zoneName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                removeInsideZones(context, zoneNames)
                Timber.d("GeofenceBroadcastReceiver: exited zones $zoneNames")
            }
        }
    }

    companion object {
        private const val PREFS = "locpc_inside_zones"
        private const val KEY   = "zones"

        /** Returns the set of zone names the device is currently inside (may be empty). */
        fun getInsideZones(context: Context): Set<String> =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet()) ?: emptySet()

        private fun addInsideZones(context: Context, names: List<String>) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.addAll(names)
            prefs.edit().putStringSet(KEY, current).apply()
            Timber.d("GeofenceBroadcastReceiver: inside zones now = $current")
        }

        private fun removeInsideZones(context: Context, names: List<String>) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.removeAll(names.toSet())
            prefs.edit().putStringSet(KEY, current).apply()
            Timber.d("GeofenceBroadcastReceiver: inside zones now = $current")
        }
    }
}

