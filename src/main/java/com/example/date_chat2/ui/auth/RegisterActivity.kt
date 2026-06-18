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
import com.example.date_chat2.data.model.ProfileUpdate
import com.example.date_chat2.data.repository.ProfileRepository
import com.example.date_chat2.network.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

class RegisterActivity : AppCompatActivity() {

    private val supabase = SupabaseManager.client
    private val profileRepository = ProfileRepository()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var fullNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var createAccountButton: AppCompatButton
    private lateinit var backToLoginButton: AppCompatButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        fullNameInput = findViewById(R.id.et_register_full_name)
        emailInput = findViewById(R.id.et_register_email)
        passwordInput = findViewById(R.id.et_register_password)
        confirmPasswordInput = findViewById(R.id.et_confirm_password)
        createAccountButton = findViewById(R.id.btn_create_account)
        backToLoginButton = findViewById(R.id.btn_back_to_login)
        progressBar = findViewById(R.id.pb_register)

        createAccountButton.setOnClickListener { register() }
        backToLoginButton.setOnClickListener { finish() }
    }

    private fun register() {
        val fullName = fullNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        when {
            fullName.isBlank() -> showMessage(R.string.full_name_required)
            email.isBlank() -> showMessage(R.string.enter_email)
            password.isBlank() -> showMessage(R.string.enter_password)
            confirmPassword.isBlank() -> showMessage(R.string.confirm_your_password)
            password != confirmPassword -> showMessage(R.string.passwords_do_not_match)
            password.length < 6 -> showMessage(R.string.password_too_short)
            else -> createAccount(fullName, email, password)
        }
    }

    private fun createAccount(fullName: String, email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val userId = registerUserAndCreateProfile(fullName, email, password)
                Log.d(TAG, "REGISTER COMPLETED userId=$userId fullName=$fullName")

                showMessage(R.string.registration_successful)

                startActivity(
                    Intent(this@RegisterActivity, ProfileSetupActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    """
                    REGISTER ERROR
                    message=${e.message}
                    cause=${e.cause}
                    class=${e.javaClass.simpleName}
                    """.trimIndent(),
                    e
                )

                Toast.makeText(
                    this@RegisterActivity,
                    friendlyRegistrationError(e),
                    Toast.LENGTH_LONG
                ).show()

                setLoading(false)
            }
        }
    }

    private suspend fun registerUserAndCreateProfile(
        fullName: String,
        email: String,
        password: String
    ): String {
        val session = registerWithSupabase(email, password)
        val userId = when {
            session?.user?.id != null -> {
                val user = session.user!!
                Log.d(TAG, "REGISTER SIGNUP returned userId=${user.id}")
                supabase.auth.importSession(session)
                user.id
            }
            else -> {
                Log.d(TAG, "REGISTER SIGNUP returned no session/user. Falling back to sign in.")
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val fallbackUserId = supabase.auth.currentSessionOrNull()?.user?.id
                    ?: supabase.auth.retrieveUserForCurrentSession(updateSession = true).id
                Log.d(TAG, "REGISTER FALLBACK sign-in userId=$fallbackUserId")
                fallbackUserId
            }
        }

        val profileSuccess = profileRepository.upsertProfile(
            ProfileUpdate(
                id = userId,
                full_name = fullName
            )
        )
        Log.d(
            TAG,
            "REGISTER PROFILE UPSERT userId=$userId fullName=$fullName success=$profileSuccess"
        )
        if (!profileSuccess) {
            throw IllegalStateException("Could not create initial profile row.")
        }
        return userId
    }

    private suspend fun registerWithSupabase(email: String, password: String): UserSession? =
        withContext(Dispatchers.IO) {
            val connection = (URL("$SUPABASE_URL/auth/v1/signup").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doInput = true
                doOutput = true
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                connection.outputStream.use { output ->
                    output.write(
                        buildJsonObject {
                            put("email", email)
                            put("password", password)
                        }.toString().toByteArray()
                    )
                    output.flush()
                }

                val responseCode = connection.responseCode
                val responseBody = readResponseBody(connection)
                Log.d(
                    TAG,
                    "REGISTER SIGNUP responseCode=$responseCode body=$responseBody"
                )

                if (responseCode == HttpURLConnection.HTTP_OK ||
                    responseCode == HttpURLConnection.HTTP_CREATED
                ) {
                    return@withContext parseSessionOrNull(responseBody)
                }

                throw IllegalStateException("Registration failed with HTTP $responseCode: $responseBody")
            } catch (e: Exception) {
                val responseBody = runCatching { readResponseBody(connection) }.getOrNull()
                if (!responseBody.isNullOrBlank()) {
                    Log.e(TAG, "REGISTER SIGNUP ERROR body=$responseBody", e)
                } else {
                    Log.e(TAG, "REGISTER SIGNUP ERROR", e)
                }
                throw e
            } finally {
                connection.disconnect()
            }
        }

    private fun parseSessionOrNull(responseBody: String?): UserSession? {
        if (responseBody.isNullOrBlank()) return null
        return try {
            json.decodeFromString<UserSession>(responseBody)
        } catch (_: SerializationException) {
            null
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = try {
            if (connection.responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
        } catch (_: Exception) {
            connection.errorStream ?: connection.inputStream
        } ?: return ""

        return stream.bufferedReader().use { it.readText() }
    }

    private fun friendlyRegistrationError(error: Exception): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            "already registered" in message || "already exists" in message ||
                "user_already_exists" in message -> getString(R.string.account_exists)
            "email confirmation" in message || "confirmation required" in message -> {
                getString(R.string.registration_requires_session)
            }
            message.contains("unknown error") ||
                message.contains("unable to resolve host") ||
                message.contains("failed to connect") ||
                message.contains("timeout") ||
                message.contains("socket") -> getString(R.string.auth_connection_error)
            else -> getString(R.string.registration_error)
        }
    }

    private fun showMessage(messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, messageRes, duration).show()
    }

    private fun setLoading(loading: Boolean) {
        createAccountButton.isEnabled = !loading
        backToLoginButton.isEnabled = !loading
        createAccountButton.text = if (loading) "" else getString(R.string.create_account)
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private companion object {
        const val TAG = "RegisterActivity"
        const val SUPABASE_URL = "https://dzzrderoixqzczvfapvx.supabase.co"
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR6enJkZXJvaXhxemN6dmZhcHZ4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE3MDI3NTgsImV4cCI6MjA5NzI3ODc1OH0.wk9kHSy6k_bDHhLRnE1oyn6KJfXZlmMJYuiyQPElXhc"
    }
}
