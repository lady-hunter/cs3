package com.example.date_chat2.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.date_chat2.R
import com.example.date_chat2.data.repository.ProfileRepository
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.auth.ProfileSetupActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvDetails: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnLogout: Button

    private val profileRepository = ProfileRepository()
    private val supabase = SupabaseManager.client

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        ivAvatar = view.findViewById(R.id.iv_profile_avatar)
        tvName = view.findViewById(R.id.tv_profile_name)
        tvDetails = view.findViewById(R.id.tv_profile_details)
        btnEditProfile = view.findViewById(R.id.btn_edit_profile)
        btnLogout = view.findViewById(R.id.btn_logout)

        btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileSetupActivity::class.java))
        }

        btnLogout.setOnClickListener {
            logout()
        }

        loadProfileData()

        return view
    }

    private fun loadProfileData() {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = profileRepository.getProfile(userId)
            profile?.let {
                tvName.text = it.full_name ?: "Unknown"
                val age = calculateAge(it.birth_date)
                tvDetails.text = "${it.gender?.replaceFirstChar { it.uppercase() }}, $age"
                
                Glide.with(this@ProfileFragment)
                    .load(it.avatar_url)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivAvatar)
            }
        }
    }

    private fun calculateAge(birthDateString: String?): Int {
        if (birthDateString == null) return 0
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val birthDate = sdf.parse(birthDateString) ?: return 0
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance()
            birth.time = birthDate
            
            var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            age
        } catch (e: Exception) {
            0
        }
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                supabase.auth.signOut()
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Logout failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
