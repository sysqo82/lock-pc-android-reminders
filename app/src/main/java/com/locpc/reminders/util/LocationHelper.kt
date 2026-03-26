package com.locpc.reminders.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): Pair<Double, Double>? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            Timber.w("Location permission not granted")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L  // Update interval
            ).build()

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(Pair(location.latitude, location.longitude))
                } else {
                    Timber.d("Last location is null, requesting new location update")
                    continuation.resume(null)
                }
            }.addOnFailureListener { exception ->
                Timber.e(exception, "Failed to get location")
                continuation.resumeWithException(exception)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception while getting location")
            continuation.resumeWithException(e)
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun getDeviceId(): String {
        val sharedPreferences = context.getSharedPreferences("locpc_device", Context.MODE_PRIVATE)
        var deviceId = sharedPreferences.getString("device_id", null)

        if (deviceId == null) {
            deviceId = System.currentTimeMillis().toString() + Math.random().toLong()
            sharedPreferences.edit().putString("device_id", deviceId).apply()
        }

        return deviceId
    }
}
