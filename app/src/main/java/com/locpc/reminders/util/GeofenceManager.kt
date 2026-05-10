package com.locpc.reminders.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.locpc.reminders.data.GeofenceZone
import com.locpc.reminders.receiver.GeofenceBroadcastReceiver
import timber.log.Timber

/**
 * Manages registration and removal of geofences with Google Play Services.
 *
 * Geofences are cleared and re-registered as a complete set every time [registerZones] is called.
 * This keeps the state consistent — adding or deleting zones on the server and then calling
 * [registerZones] will always reflect the current server state.
 *
 * Callers are responsible for ensuring that [Manifest.permission.ACCESS_FINE_LOCATION] and
 * [Manifest.permission.ACCESS_BACKGROUND_LOCATION] are granted before calling [registerZones].
 */
object GeofenceManager {

    private const val PENDING_INTENT_REQUEST_CODE = 7001

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Clear all existing geofences and register [zones].
     * Does nothing if location permission is missing or [zones] is empty.
     */
    fun registerZones(context: Context, zones: List<GeofenceZone>) {
        val client: GeofencingClient = LocationServices.getGeofencingClient(context)

        // Always remove existing geofences first so we start clean
        client.removeGeofences(buildPendingIntent(context))
            .addOnCompleteListener {
                if (zones.isEmpty()) {
                    Timber.d("GeofenceManager: no zones to register")
                    return@addOnCompleteListener
                }

                if (!hasRequiredPermissions(context)) {
                    Timber.w("GeofenceManager: location permissions not granted, skipping registration")
                    return@addOnCompleteListener
                }

                val geofences = zones.map { zone ->
                    // Normalize longitude to [-180, 180] — values outside this range
                    // can be stored if the map was panned past the date line
                    val lng = ((zone.longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    Geofence.Builder()
                        .setRequestId(zone.name)
                        .setCircularRegion(zone.latitude, lng, zone.radiusMeters.toFloat())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(
                            Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_EXIT or
                            Geofence.GEOFENCE_TRANSITION_DWELL
                        )
                        .setLoiteringDelay(30_000) // 30 s dwell before DWELL fires
                        .build()
                }

                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(
                        GeofencingRequest.INITIAL_TRIGGER_ENTER or
                        GeofencingRequest.INITIAL_TRIGGER_DWELL
                    )
                    .addGeofences(geofences)
                    .build()

                try {
                    client.addGeofences(request, buildPendingIntent(context))
                        .addOnSuccessListener {
                            Timber.d("GeofenceManager: registered ${geofences.size} geofence(s)")
                            persistZones(context, zones)
                        }
                        .addOnFailureListener { e ->
                            Timber.e(e, "GeofenceManager: failed to register geofences")
                        }
                } catch (e: SecurityException) {
                    Timber.e(e, "GeofenceManager: SecurityException adding geofences")
                }
            }
    }

    /** Remove all registered geofences. */
    fun removeAll(context: Context) {
        LocationServices.getGeofencingClient(context)
            .removeGeofences(buildPendingIntent(context))
            .addOnSuccessListener { Timber.d("GeofenceManager: all geofences removed") }
            .addOnFailureListener { e -> Timber.e(e, "GeofenceManager: failed to remove geofences") }
        clearPersistedZones(context)
    }

    /**
     * Persist zones to SharedPreferences so [BootReceiver] can re-register them after reboot
     * without a network call.
     */
    private fun persistZones(context: Context, zones: List<GeofenceZone>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = zones.joinToString(separator = ";") { z ->
            "${z.id},${z.name.replace(",", "").replace(";", "")},${z.latitude},${z.longitude},${z.radiusMeters}"
        }
        prefs.edit().putString(KEY_ZONES, json).apply()
        Timber.d("GeofenceManager: persisted ${zones.size} zone(s)")
    }

    private fun clearPersistedZones(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ZONES).apply()
    }

    /** Load zones that were previously persisted (used by BootReceiver). */
    fun loadPersistedZones(context: Context): List<GeofenceZone> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ZONES, null) ?: return emptyList()
        return try {
            json.split(";").mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size < 5) null
                else GeofenceZone(
                    id = parts[0].toInt(),
                    name = parts[1],
                    latitude = parts[2].toDouble(),
                    longitude = parts[3].toDouble(),
                    radiusMeters = parts[4].toInt()
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "GeofenceManager: failed to parse persisted zones")
            emptyList()
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val background = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    private const val PREFS_NAME = "locpc_geofences"
    private const val KEY_ZONES = "zones"
}
