package com.example.date_chat2.ui.main.swipe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.date_chat2.R
import com.example.date_chat2.data.model.Profile
import java.time.LocalDate
import java.time.Period

class ProfileCardAdapter(
    private val profiles: List<Profile>
) : RecyclerView.Adapter<ProfileCardAdapter.ProfileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(profiles[position])
    }

    override fun getItemCount(): Int = profiles.size

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.iv_profile_image)
        private val nameAndAge: TextView = itemView.findViewById(R.id.tv_profile_name_age)
        private val bio: TextView = itemView.findViewById(R.id.tv_profile_bio)

        fun bind(profile: Profile) {
            val name = profile.full_name?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.unknown_name)
            val age = calculateAge(profile.birth_date)
            nameAndAge.text = if (age == null) name else "$name, $age"
            bio.text = profile.bio?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.no_bio)

            Glide.with(itemView)
                .load(profile.avatar_url)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .centerCrop()
                .into(avatar)
        }

        private fun calculateAge(birthDate: String?): Int? {
            return try {
                birthDate?.let { Period.between(LocalDate.parse(it), LocalDate.now()).years }
                    ?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }
    }
}
