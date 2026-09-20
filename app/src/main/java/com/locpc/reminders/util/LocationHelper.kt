package com.locpc.reminders.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Triple<Double, Double, Float>? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            Timber.w("Location permission not granted")
            if (continuation.isActive) {
                continuation.resume(null)
            }
            return@suspendCancellableCoroutine
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (!continuation.isActive) return@addOnSuccessListener

                if (location != null) {
                    continuation.resume(Triple(location.latitude, location.longitude, location.accuracy))
                } else {
                    Timber.d("Last location is null, requesting fresh location")
                    val cts = CancellationTokenSource()
                    continuation.invokeOnCancellation { cts.cancel() }
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(0)
                        .build()
                    fusedLocationClient.getCurrentLocation(request, cts.token)
                        .addOnSuccessListener { fresh ->
                            if (continuation.isActive) {
                                if (fresh != null) {
                                    continuation.resume(Triple(fresh.latitude, fresh.longitude, fresh.accuracy))
                                } else {
                                    Timber.w("Fresh location also null")
                                    continuation.resume(null)
                                }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Timber.e(exception, "Failed to get fresh location")
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }
                }
            }.addOnFailureListener { exception ->
                Timber.e(exception, "Failed to get location")
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while getting location")
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
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
