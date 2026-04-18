package com.locpc.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.locpc.reminders.api.ApiManager
import com.locpc.reminders.data.Reminder
import com.locpc.reminders.ui.ReminderAdapter
import com.locpc.reminders.util.NotificationHelper
import com.locpc.reminders.worker.ReminderSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var recyclerView: RecyclerView
    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var logoutButton: Button
    private val reminders = mutableListOf<Reminder>()

    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            fetchReminders()
            pollHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val POLL_INTERVAL_MS = 15_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark icons on light background (Android 15+ edge-to-edge)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        // Check if user is logged in
        if (!ApiManager.isLoggedIn()) {
            Timber.d("User not logged in, navigating to login")
            navigateToLogin()
            return
        }

        setContentView(R.layout.activity_main)

        Timber.d("MainActivity: onCreate called")

        // Request necessary permissions
        requestPermissions()

        // Initialize UI
        setupRecyclerView()
        setupLogoutButton()

        // Show cached data immediately, live data comes via onResume polling
        loadLocalReminders()

        // Schedule background sync via WorkManager (runs every 15 min when app is closed)
        scheduleBackgroundSync()
    }

    override fun onResume() {
        super.onResume()
        // Re-check exact alarm permission each time we come back (user may have just granted it in Settings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (alarmManager.canScheduleExactAlarms()) {
                // Permission is now granted — reschedule any reminders that were previously skipped
                val notificationHelper = NotificationHelper(this)
                for (reminder in reminders) {
                    notificationHelper.scheduleReminder(reminder)
                }
            }
        }
        // Android 14+ also requires USE_FULL_SCREEN_INTENT to be explicitly granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Full-screen intent permission needed")
                    .setMessage("On Android 14+, \"Alarms & reminders\" must also be enabled in Settings for full-screen alerts.\n\nTap OK to open Settings.")
                    .setCancelable(true)
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.fromParts("package", packageName, null)
                        })
                    }
                    .setNegativeButton("Not now", null)
                    .show()
            }
        }
        // Connect Socket.IO for real-time reminder pushes.
        // onLocateDevice is intentionally NOT set here — SocketService owns it so the
        // locate_device event is handled even when MainActivity is in the background.
        SocketManager.onRemindersUpdated = { updated -> applyReminders(updated) }
        SocketManager.onForceLogout = { performLogout() }
        // Start (or restart) the persistent socket foreground service. It handles
        // connect() and the locate_device callback internally.
        com.locpc.reminders.service.SocketService.start(this)
        // Start foreground polling immediately when activity is visible
        pollHandler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Stop polling when activity goes to background (WorkManager takes over)
        // Do NOT disconnect socket — force_logout events must be receivable at all times
        pollHandler.removeCallbacks(pollRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.reminderRecyclerView)
        reminderAdapter = ReminderAdapter(reminders)
        recyclerView.adapter = reminderAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupLogoutButton() {
        logoutButton = findViewById(R.id.logoutButton)
        logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun loadLocalReminders() {
        val sharedPreferences = getSharedPreferences("locpc_reminders", MODE_PRIVATE)
        val remindersJson = sharedPreferences.getString("reminders_list", null)

        if (!remindersJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<Reminder>>() {}.type
                val loadedReminders: List<Reminder> = Gson().fromJson(remindersJson, type)
                reminders.clear()
                reminders.addAll(loadedReminders)
                reminderAdapter.notifyDataSetChanged()
                Timber.d("Loaded ${reminders.size} reminders from local storage")
            } catch (e: Exception) {
                Timber.e(e, "Error loading reminders from local storage")
            }
        }
    }

    private fun fetchReminders() {
        scope.launch {
            try {
                val apiService = ApiManager.getApiService()
                val response = apiService.getReminders()

                if (response.isSuccessful) {
                    applyReminders(response.body() ?: emptyList())
                } else if (response.code() == 401) {
                    Timber.e("fetchReminders: 401 Unauthorized")
                } else {
                    Timber.e("Failed to fetch reminders: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching reminders")
            }
        }
    }

    /** Apply a fresh reminder list from any source (HTTP poll or Socket.IO push). */
    private fun applyReminders(updated: List<Reminder>) {
        val prefs = getSharedPreferences("locpc_reminders", MODE_PRIVATE)
        val notificationHelper = NotificationHelper(this)

        // Cancel alarms for any reminders that were removed
        val oldJson = prefs.getString("reminders_list", null)
        if (!oldJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Reminder>>() {}.type
                val oldReminders: List<Reminder> = Gson().fromJson(oldJson, type)
                val updatedIds = updated.map { it.id }.toSet()
                val editor = prefs.edit()
                for (old in oldReminders) {
                    if (old.id !in updatedIds) {
                        notificationHelper.cancelReminderAlarms(old.id)
                        editor.remove("notified_${old.id}")
                        Timber.d("Cancelled alarms for removed reminder: ${old.id}")
                    }
                }
                editor.apply()
            } catch (e: Exception) {
                Timber.e(e, "applyReminders: failed to parse old reminders for diff")
            }
        }

        reminders.clear()
        reminders.addAll(updated)
        reminderAdapter.notifyDataSetChanged()
        Timber.d("Reminders applied: ${updated.size} items")

        prefs.edit()
            .putString("reminders_list", Gson().toJson(updated))
            .putLong("last_sync", System.currentTimeMillis())
            .apply()

        for (reminder in updated) {
            notificationHelper.scheduleReminder(reminder)
        }
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<ReminderSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reminder_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun performLogout() {
        scope.launch {
            try {
                logoutButton.isEnabled = false

                // Cancel all scheduled alarms before clearing session
                NotificationHelper(this@MainActivity).cancelAllAlarms()

                // Stop the background sync worker
                WorkManager.getInstance(this@MainActivity).cancelUniqueWork("reminder_sync")

                // Clear locally cached reminders
                getSharedPreferences("locpc_reminders", MODE_PRIVATE).edit()
                    .remove("reminders_list")
                    .remove("last_sync")
                    .apply()

                // Disconnect socket
                SocketManager.disconnect()

                // Call logout API to clear server-side session
                ApiManager.logout()

                Toast.makeText(this@MainActivity, "Logged out", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } catch (e: Exception) {
                Timber.e(e, "Error during logout")
                Toast.makeText(
                    this@MainActivity,
                    "Logout error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                logoutButton.isEnabled = true
            }
        }
    }

    private fun startLocationService() {
        // On Android 12+ calling startForegroundService() from a background context throws
        // ForegroundServiceStartNotAllowedException.  Use WorkManager expedited work instead,
        // which internally manages the foreground service and is always allowed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val request = androidx.work.OneTimeWorkRequestBuilder<com.locpc.reminders.worker.LocationWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    "location_update",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
        } else {
            val intent = Intent(this, com.locpc.reminders.service.LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Location permissions (fine + coarse first, background must be separate on Android 11+)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // ACCESS_BACKGROUND_LOCATION must be requested in its own separate dialog on Android 11+.
        // Only request it when fine/coarse is already granted to avoid the system silently denying it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val bgGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fineGranted && !bgGranted) {
                // Separate request so Android shows the "Allow all the time" rationale.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            } else if (!fineGranted) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }

        // Exact alarm permission (Android 12+) — requires a manual Settings grant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Allow \"Alarms & Reminders\" for on-time notifications",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
        }

        // Battery optimization — must be disabled for alarms to fire reliably
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(
                this,
                "Disable battery optimization so reminders fire on time",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for ((i, permission) in permissions.withIndex()) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Timber.d("Permission granted: $permission")
                } else {
                    Timber.d("Permission denied: $permission")
                }
            }
        }
    }
}
