package com.locpc.reminders

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.locpc.reminders.receiver.WifiStateReceiver
import timber.log.Timber

class WifiAlertActivity : AppCompatActivity() {

    private var zoneName: String = "this area"
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_wifi_alert)

        zoneName = intent.getStringExtra(EXTRA_ZONE_NAME) ?: "this area"
        findViewById<TextView>(R.id.wifiAlertZone).text =
            "You entered \"$zoneName\". Turn on Wi-Fi to stay connected."

        findViewById<Button>(R.id.turnOnButton).setOnClickListener {
            openWifiPanel()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        if (wm?.isWifiEnabled == true) {
            Timber.d("WifiAlertActivity: Wi-Fi now on, dismissing")
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        // If the user pressed Home and Wi-Fi is still off, bring ourselves back to the front
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        if (wm?.isWifiEnabled != true) {
            Timber.d("WifiAlertActivity: sent to background with Wi-Fi still off — re-launching")
            handler.postDelayed({
                val relaunch = Intent(applicationContext, WifiAlertActivity::class.java).apply {
                    putExtra(EXTRA_ZONE_NAME, zoneName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                applicationContext.startActivity(relaunch)
            }, 600)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // Cancel the Wi-Fi alert notification posted by WifiStateReceiver (if any)
        NotificationManagerCompat.from(this).cancel(WifiStateReceiver.NOTIFICATION_ID)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        // Block back — only Wi-Fi being turned on exits this screen
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun openWifiPanel() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            @Suppress("DEPRECATION")
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    companion object {
        const val EXTRA_ZONE_NAME = "zone_name"
    }
}

