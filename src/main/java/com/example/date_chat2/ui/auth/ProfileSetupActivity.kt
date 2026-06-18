package com.example.date_chat2.ui.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.date_chat2.R
import com.example.date_chat2.data.model.ProfileUpdate
import com.example.date_chat2.data.repository.ProfileRepository
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.HomeActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.util.Calendar

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var avatarView: ImageView
    private lateinit var fullNameInput: EditText
    private lateinit var birthDateInput: EditText
    private lateinit var bioInput: EditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar

    private var selectedImageUri: Uri? = null
    private var existingAvatarUrl: String? = null
    private val profileRepository = ProfileRepository()
    private val supabase = SupabaseManager.client

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            avatarView.setPadding(0, 0, 0, 0)
            avatarView.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        avatarView = findViewById(R.id.iv_avatar)
        fullNameInput = findViewById(R.id.et_full_name)
        birthDateInput = findViewById(R.id.et_birth_date)
        bioInput = findViewById(R.id.et_bio)
        genderGroup = findViewById(R.id.rg_gender)
        saveButton = findViewById(R.id.btn_save_profile)
        progressBar = findViewById(R.id.pb_loading)

        val openGallery = View.OnClickListener { imagePickerLauncher.launch("image/*") }
        avatarView.setOnClickListener(openGallery)
        findViewById<ImageButton>(R.id.btn_add_photo).setOnClickListener(openGallery)
        findViewById<Button>(R.id.btn_choose_photo).setOnClickListener(openGallery)
        birthDateInput.setOnClickListener { showDatePicker() }
        saveButton.setOnClickListener { saveProfile() }

        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        setLoading(true)
        lifecycleScope.launch {
            profileRepository.getProfile(userId)?.let { profile ->
                fullNameInput.setText(profile.full_name.orEmpty())
                birthDateInput.setText(profile.birth_date.orEmpty())
                bioInput.setText(profile.bio.orEmpty())
                when (profile.gender) {
                    "male" -> genderGroup.check(R.id.rb_male)
                    "female" -> genderGroup.check(R.id.rb_female)
                }
                existingAvatarUrl = profile.avatar_url
                if (!existingAvatarUrl.isNullOrBlank()) {
                    avatarView.setPadding(0, 0, 0, 0)
                    Glide.with(this@ProfileSetupActivity)
                        .load(existingAvatarUrl)
                        .placeholder(R.drawable.bg_avatar_placeholder)
                        .into(avatarView)
                }
            }
            setLoading(false)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                birthDateInput.setText(String.format("%04d-%02d-%02d", year, month + 1, day))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun saveProfile() {
        val fullName = fullNameInput.text.toString().trim()
        val birthDate = birthDateInput.text.toString().trim()
        val bio = bioInput.text.toString().trim()
        val genderId = genderGroup.checkedRadioButtonId

        if (fullName.isEmpty() || birthDate.isEmpty() || genderId == -1) {
            Toast.makeText(this, R.string.profile_required_fields, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedImageUri == null && existingAvatarUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.profile_photo_required, Toast.LENGTH_SHORT).show()
            return
        }

        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: run {
            Toast.makeText(this, R.string.session_expired, Toast.LENGTH_SHORT).show()
            return
        }
        val gender = if (genderId == R.id.rb_male) "male" else "female"
        setLoading(true)

        lifecycleScope.launch {
            try {
                val avatarUrl = selectedImageUri?.let { uri ->
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("Cannot read the selected image.")
                    profileRepository.uploadAvatar(userId, bytes)
                        ?: throw Exception("Could not upload the profile photo.")
                } ?: existingAvatarUrl

                val success = profileRepository.upsertProfile(
                    ProfileUpdate(
                        id = userId,
                        full_name = fullName,
                        avatar_url = avatarUrl,
                        gender = gender,
                        birth_date = birthDate,
                        bio = bio.ifBlank { null }
                    )
                )
                if (!success) throw Exception("Could not save profile information.")

                Toast.makeText(
                    this@ProfileSetupActivity,
                    R.string.profile_saved,
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(Intent(this@ProfileSetupActivity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@ProfileSetupActivity,
                    "Error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        saveButton.isEnabled = !loading
        saveButton.text = if (loading) "" else getString(R.string.save_profile)
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
