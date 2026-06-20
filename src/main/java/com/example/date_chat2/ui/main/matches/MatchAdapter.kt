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
    private var unreadCounts = emptyMap<String, Int>()

    fun submitList(newItems: List<MatchItem>, newUnreadCounts: Map<String, Int>) {
        items.clear()
        items.addAll(newItems)
        unreadCounts = newUnreadCounts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_match, parent, false)
        return MatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, unreadCounts[item.otherUserId] ?: 0, onClick)
    }

    override fun getItemCount(): Int = items.size

    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.iv_match_avatar)
        private val name: TextView = itemView.findViewById(R.id.tv_match_name)
        private val status: TextView = itemView.findViewById(R.id.tv_match_status)
        private val bio: TextView = itemView.findViewById(R.id.tv_match_bio)
        private val onlineDot: View = itemView.findViewById(R.id.view_online_dot)
        private val unreadBadge: TextView = itemView.findViewById(R.id.tv_unread_badge)

        fun bind(item: MatchItem, unreadCount: Int, onClick: (MatchItem) -> Unit) {
            val profile = item.profile
            name.text = profile.full_name?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.unknown_name)
            val isOnline = profile.is_online == true
            status.setText(if (isOnline) R.string.online else R.string.last_seen_recently)
            status.setTextColor(
                itemView.context.getColor(
                    if (isOnline) R.color.green_like else R.color.text_secondary
                )
            )
            onlineDot.visibility = if (isOnline) View.VISIBLE else View.GONE
            bio.text = profile.bio?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.start_conversation)
            unreadBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
            unreadBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()

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
