package com.example.date_chat2.ui.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.date_chat2.R
import com.example.date_chat2.data.model.ProfileUpdate
import com.example.date_chat2.data.repository.ProfileRepository
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.HomeActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.util.*

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var ivAvatar: ImageView
    private lateinit var etFullName: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var etBirthDate: EditText
    private lateinit var btnSave: Button
    private lateinit var pbLoading: ProgressBar

    private var selectedImageUri: Uri? = null
    private val profileRepository = ProfileRepository()
    private val supabase = SupabaseManager.client

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            ivAvatar.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        ivAvatar = findViewById(R.id.iv_avatar)
        etFullName = findViewById(R.id.et_full_name)
        rgGender = findViewById(R.id.rg_gender)
        etBirthDate = findViewById(R.id.et_birth_date)
        btnSave = findViewById(R.id.btn_save_profile)
        pbLoading = findViewById(R.id.pb_loading)

        ivAvatar.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        etBirthDate.setOnClickListener {
            showDatePicker()
        }

        btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val date = String.format("%04d-%02d-%02d", year, month + 1, day)
                etBirthDate.setText(date)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveProfile() {
        val fullName = etFullName.text.toString().trim()
        val birthDate = etBirthDate.text.toString().trim()
        val genderId = rgGender.checkedRadioButtonId
        
        if (fullName.isEmpty() || birthDate.isEmpty() || genderId == -1) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Vui lòng chọn ảnh đại diện", Toast.LENGTH_SHORT).show()
            return
        }

        val gender = if (genderId == R.id.rb_male) "male" else "female"
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: run {
            Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val bytes = contentResolver.openInputStream(selectedImageUri!!)?.readBytes()
                    ?: throw Exception("Cannot read image file.")

                val avatarUrl = profileRepository.uploadAvatar(userId, bytes)
                    ?: throw Exception("Failed to upload avatar.")

                val profileUpdate = ProfileUpdate(
                    id = userId,
                    full_name = fullName,
                    avatar_url = avatarUrl,
                    gender = gender,
                    birth_date = birthDate
                )
                
                val success = profileRepository.upsertProfile(profileUpdate)

                if (success) {
                    Toast.makeText(this@ProfileSetupActivity, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@ProfileSetupActivity, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    throw Exception("Failed to save profile information.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ProfileSetupActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            btnSave.isEnabled = false
            btnSave.text = ""
            pbLoading.visibility = View.VISIBLE
        } else {
            btnSave.isEnabled = true
            btnSave.text = "Save and Continue"
            pbLoading.visibility = View.GONE
        }
    }
}
