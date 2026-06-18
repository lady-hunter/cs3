package com.example.date_chat2.ui.main.swipe

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
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

            val avatarUrl = profile.avatar_url?.trim()?.takeIf { it.isNotEmpty() }
            if (avatarUrl == null) {
                Glide.with(itemView).clear(avatar)
                avatar.setImageResource(PLACEHOLDER_RES_ID)
                return
            }

            Glide.with(itemView)
                .load(avatarUrl)
                .placeholder(PLACEHOLDER_RES_ID)
                .error(PLACEHOLDER_RES_ID)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(
                            TAG,
                            "SWIPE AVATAR LOAD FAILED profileId=${profile.id} avatarUrl=$avatarUrl",
                            e
                        )
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean = false
                })
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

        private companion object {
            const val TAG = "ProfileCardAdapter"
            const val PLACEHOLDER_RES_ID = android.R.drawable.ic_menu_gallery
        }
    }
}
