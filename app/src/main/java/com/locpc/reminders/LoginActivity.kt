package com.locpc.reminders

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import com.locpc.reminders.api.ApiManager

class LoginActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark icons on light background (Android 15+ edge-to-edge)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        // Check if already logged in
        val isLoggedIn = ApiManager.isLoggedIn()
        if (isLoggedIn) {
            Timber.d("User already logged in, navigating to reminders")
            navigateToReminders()
            return
        }

        setContentView(R.layout.activity_login)

        // Apply top/bottom insets while enforcing a fixed 24dp horizontal padding
        val loginRoot = findViewById<View>(R.id.loginRoot)
        ViewCompat.setOnApplyWindowInsetsListener(loginRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val dp24 = (24 * resources.displayMetrics.density + 0.5f).toInt()
            ViewCompat.setPaddingRelative(view, dp24, bars.top + dp24, dp24, bars.bottom)
            insets
        }

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        progressBar = findViewById(R.id.progressBar)

        loginButton.setOnClickListener {
            performLogin()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        }
    }

    private fun performLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                loginButton.isEnabled = false
                progressBar.visibility = android.view.View.VISIBLE

                val apiService = ApiManager.getApiService()
                
                try {
                    val response = apiService.login(email, password)

                    if (response.isSuccessful) {
                        Timber.d("Login response successful - status: ${response.code()}")
                        Timber.d("Response headers: ${response.headers()}")
                        
                        // Login successful - session cookie is stored automatically
                        // Verify we can fetch reminders to confirm authentication
                        try {
                            val remindersResponse = apiService.getReminders()
                            if (remindersResponse.isSuccessful) {
                                // Mark as logged in
                                ApiManager.setLoggedIn(email)
                                Timber.d("Login successful for $email")
                                
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Login successful",
                                    Toast.LENGTH_SHORT
                                ).show()

                                navigateToReminders()
                            } else {
                                Timber.e("Failed to fetch reminders after login: ${remindersResponse.code()}")
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Login verified but couldn't fetch reminders",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to verify login by fetching reminders")
                            Toast.makeText(
                                this@LoginActivity,
                                "Login verification failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Timber.e("Login failed: ${response.code()} - $errorBody")
                        Toast.makeText(
                            this@LoginActivity,
                            "Login failed: Invalid credentials",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during login request: ${e.message}")
                    Toast.makeText(
                        this@LoginActivity,
                        "Connection error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                loginButton.isEnabled = true
                progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun navigateToReminders() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}

