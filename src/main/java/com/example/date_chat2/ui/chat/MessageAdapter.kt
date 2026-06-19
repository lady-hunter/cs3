package com.example.date_chat2.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.date_chat2.R
import com.example.date_chat2.data.Message
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MessageAdapter(private val currentUserId: String) : 
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var messages = listOf<Message>()
    private var senderNames = emptyMap<String, String>()

    fun submitList(newList: List<Message>, newSenderNames: Map<String, String>) {
        messages = newList
        senderNames = newSenderNames
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
        private val mediaLayout: FrameLayout = itemView.findViewById(R.id.layout_message_media)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_message_image)
        private val ivVideoPlay: ImageView = itemView.findViewById(R.id.iv_video_play)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tv_timestamp)
        private val layoutRoot: LinearLayout = itemView as LinearLayout

        fun bind(message: Message) {
            val isImage = message.message_type == MESSAGE_TYPE_IMAGE && !message.image_url.isNullOrBlank()
            val isVideo = message.message_type == MESSAGE_TYPE_VIDEO && !message.image_url.isNullOrBlank()
            val hasMedia = isImage || isVideo
            tvContent.visibility = if (hasMedia) View.GONE else View.VISIBLE
            mediaLayout.visibility = if (hasMedia) View.VISIBLE else View.GONE
            ivVideoPlay.visibility = if (isVideo) View.VISIBLE else View.GONE
            tvContent.text = message.content
            tvContent.maxWidth = (itemView.resources.displayMetrics.widthPixels * 0.75f).toInt()
            if (hasMedia) {
                val request = Glide.with(itemView)
                    .load(message.image_url)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                if (isVideo) {
                    request.apply(RequestOptions().frame(VIDEO_THUMBNAIL_TIME_US))
                }
                request.into(ivImage)
                mediaLayout.setOnClickListener {
                    if (isVideo) openVideoPlayer(message.image_url)
                    else openImagePreview(message.image_url)
                }
            } else {
                Glide.with(itemView).clear(ivImage)
                mediaLayout.setOnClickListener(null)
            }
            tvTimestamp.text = formatMessageTime(message.created_at)
            tvTimestamp.visibility = if (tvTimestamp.text.isEmpty()) View.GONE else View.VISIBLE
            
            if (message.sender_id == currentUserId) {
                layoutRoot.gravity = Gravity.END
                tvSender.visibility = View.GONE
                tvTimestamp.gravity = Gravity.END
                tvContent.setBackgroundResource(R.drawable.bg_message_mine)
                ivImage.setBackgroundResource(R.drawable.bg_message_mine)
                tvContent.setTextColor(itemView.context.getColor(R.color.white))
            } else {
                layoutRoot.gravity = Gravity.START
                tvSender.visibility = View.VISIBLE
                tvSender.text = senderNames[message.sender_id]
                    ?: itemView.context.getString(R.string.unknown_user)
                tvTimestamp.gravity = Gravity.START
                tvContent.setBackgroundResource(R.drawable.bg_message_other)
                ivImage.setBackgroundResource(R.drawable.bg_message_other)
                tvContent.setTextColor(itemView.context.getColor(R.color.text_primary))
            }
        }

        private fun openImagePreview(imageUrl: String?) {
            if (imageUrl.isNullOrBlank()) return
            val activity = itemView.context as? FragmentActivity ?: return
            if (activity.supportFragmentManager.findFragmentByTag(ImagePreviewDialogFragment.TAG) != null) {
                return
            }
            ImagePreviewDialogFragment.newInstance(imageUrl)
                .show(activity.supportFragmentManager, ImagePreviewDialogFragment.TAG)
        }

        private fun openVideoPlayer(videoUrl: String?) {
            if (videoUrl.isNullOrBlank()) return
            val activity = itemView.context as? FragmentActivity ?: return
            if (activity.supportFragmentManager.findFragmentByTag(VideoPlayerDialogFragment.TAG) != null) {
                return
            }
            VideoPlayerDialogFragment.newInstance(videoUrl)
                .show(activity.supportFragmentManager, VideoPlayerDialogFragment.TAG)
        }

        private fun formatMessageTime(createdAt: String?): String {
            if (createdAt.isNullOrBlank()) return ""

            val instant = runCatching { Instant.parse(createdAt) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
                ?: return ""
            return TIME_FORMATTER.format(instant)
        }
    }

    private companion object {
        const val MESSAGE_TYPE_IMAGE = "image"
        const val MESSAGE_TYPE_VIDEO = "video"
        const val VIDEO_THUMBNAIL_TIME_US = 1_000_000L
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
    }
}
