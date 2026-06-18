package com.example.date_chat2.ui.main.matches

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.date_chat2.R
import com.example.date_chat2.data.model.MatchItem

class MatchAdapter(
    private val onClick: (MatchItem) -> Unit
) : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {

    private val items = mutableListOf<MatchItem>()

    fun submitList(newItems: List<MatchItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_match, parent, false)
        return MatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.iv_match_avatar)
        private val name: TextView = itemView.findViewById(R.id.tv_match_name)
        private val bio: TextView = itemView.findViewById(R.id.tv_match_bio)

        fun bind(item: MatchItem, onClick: (MatchItem) -> Unit) {
            val profile = item.profile
            name.text = profile.full_name?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.unknown_name)
            bio.text = profile.bio?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.no_bio)

            Log.d(
                TAG,
                "MATCH BIND matchId=${item.matchId} otherUserId=${item.otherUserId} avatarUrl=${profile.avatar_url}"
            )

            Glide.with(itemView)
                .load(profile.avatar_url)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .centerCrop()
                .into(avatar)

            itemView.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        const val TAG = "MatchAdapter"
    }
}
