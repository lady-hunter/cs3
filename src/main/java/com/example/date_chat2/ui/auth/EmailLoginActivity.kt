package com.example.date_chat2.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.date_chat2.R
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.HomeActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class EmailLoginActivity : AppCompatActivity() {

    private var isLoginMode = true
    private val supabase = SupabaseManager.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_login)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnAction = findViewById<AppCompatButton>(R.id.btn_action)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val etConfirmPassword = findViewById<EditText>(R.id.et_confirm_password)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        val tvSwitchMode = findViewById<TextView>(R.id.tv_switch_mode)

        btnBack.setOnClickListener {
            finish()
        }

        tvSwitchMode.setOnClickListener {
            isLoginMode = !isLoginMode
            if (isLoginMode) {
                tvTitle.text = "Welcome Back"
                btnAction.text = "LOG IN"
                tvSwitchMode.text = "Don't have an account? Sign Up"
                etConfirmPassword.isVisible = false
            } else {
                tvTitle.text = "Create Account"
                btnAction.text = "SIGN UP"
                tvSwitchMode.text = "Already have an account? Log In"
                etConfirmPassword.isVisible = true
            }
        }

        btnAction.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isLoginMode) {
                if (password != confirmPassword) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                signUp(email, password)
            } else {
                login(email, password)
            }
        }
    }

    private fun signUp(email: String, password: String) {
        lifecycleScope.launch {
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                Toast.makeText(this@EmailLoginActivity, "Registration successful! Check your email if verification is required.", Toast.LENGTH_LONG).show()
                checkSessionAndNavigate()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EmailLoginActivity, "Sign Up Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun login(email: String, password: String) {
        lifecycleScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                checkSessionAndNavigate()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EmailLoginActivity, "Login Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkSessionAndNavigate() {
        lifecycleScope.launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session?.user != null) {
                startActivity(Intent(this@EmailLoginActivity, HomeActivity::class.java))
                finish()
            }
        }
    }
}
