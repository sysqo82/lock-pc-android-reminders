package com.locpc.reminders

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReminderAlertActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private var currentReminderId = ""

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

        setContentView(R.layout.activity_reminder_alert)
        titleView = findViewById(R.id.alertTitle)
        descView = findViewById(R.id.alertDescription)
        findViewById<Button>(R.id.dismissButton).setOnClickListener { dismiss() }

        handleAlarmIntent(intent)
    }

    /** Called when a second alarm fires while this activity is already showing (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(src: Intent) {
        currentReminderId = src.getStringExtra("reminder_id") ?: return
        val title = src.getStringExtra("reminder_title") ?: "Reminder"
        val description = src.getStringExtra("reminder_description")

        titleView.text = title
        if (!description.isNullOrBlank()) {
            descView.text = description
            descView.visibility = View.VISIBLE
        } else {
            descView.visibility = View.GONE
        }
    }

    private fun dismiss() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(currentReminderId.hashCode())
        finish()
    }
}
