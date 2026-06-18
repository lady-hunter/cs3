package com.example.date_chat2.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.date_chat2.R
import com.example.date_chat2.data.Message

class MessageAdapter(private val currentUserId: String) : 
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var messages = listOf<Message>()

    fun submitList(newList: List<Message>) {
        messages = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount() = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tv_sender)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val layoutRoot: LinearLayout = itemView as LinearLayout

        fun bind(message: Message) {
            tvContent.text = message.content
            
            if (message.sender_id == currentUserId) {
                layoutRoot.gravity = Gravity.END
                tvSender.visibility = View.GONE
                tvContent.setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame)
            } else {
                layoutRoot.gravity = Gravity.START
                tvSender.visibility = View.VISIBLE
                tvSender.text = "User: ${message.sender_id.take(5)}"
                tvContent.setBackgroundResource(android.R.drawable.editbox_dropdown_dark_frame)
            }
        }
    }
}
