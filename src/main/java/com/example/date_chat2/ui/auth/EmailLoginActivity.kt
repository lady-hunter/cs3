package com.example.date_chat2.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.example.date_chat2.R
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.HomeActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class EmailLoginActivity : AppCompatActivity() {

    private val supabase = SupabaseManager.client
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: AppCompatButton
    private lateinit var registerButton: AppCompatButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (supabase.auth.currentSessionOrNull()?.user != null) {
            openHome()
            return
        }

        setContentView(R.layout.activity_email_login)
        emailInput = findViewById(R.id.et_email)
        passwordInput = findViewById(R.id.et_password)
        loginButton = findViewById(R.id.btn_login)
        registerButton = findViewById(R.id.btn_register)
        progressBar = findViewById(R.id.pb_auth)

        loginButton.setOnClickListener { login() }
        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun login() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isBlank()) {
            Toast.makeText(this, R.string.enter_email, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isBlank()) {
            Toast.makeText(this, R.string.enter_password, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                openHome()
            } catch (e: Exception) {
                Log.e(TAG, "Supabase email login failed", e)
                Toast.makeText(
                    this@EmailLoginActivity,
                    friendlyLoginError(e),
                    Toast.LENGTH_LONG
                ).show()
                setLoading(false)
            }
        }
    }

    private fun friendlyLoginError(error: Exception): Int {
        val message = error.message.orEmpty().lowercase()
        return when {
            "invalid login credentials" in message || "invalid_credentials" in message -> {
                R.string.invalid_login
            }
            "email not confirmed" in message || "confirmation" in message -> {
                R.string.registration_requires_session
            }
            message.contains("unknown error url") ||
                message.contains("unable to resolve host") ||
                message.contains("failed to connect") ||
                message.contains("timeout") ||
                message.contains("socket") -> R.string.auth_connection_error
            else -> R.string.login_error
        }
    }

    private fun setLoading(loading: Boolean) {
        loginButton.isEnabled = !loading
        registerButton.isEnabled = !loading
        loginButton.text = if (loading) "" else getString(R.string.login)
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private companion object {
        const val TAG = "EmailLoginActivity"
    }
}
