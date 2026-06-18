package com.example.date_chat2.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.webkit.MimeTypeMap
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.date_chat2.R
import com.example.date_chat2.data.Message
import com.example.date_chat2.data.model.Profile
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.MainActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnEmoji: Button
    private lateinit var btnImage: ImageButton
    private lateinit var btnSend: Button
    private lateinit var emojiPanel: View
    private lateinit var emojiGrid: GridLayout
    private lateinit var btnLogout: Button
    private lateinit var currentUserId: String
    private lateinit var matchedUserId: String
    
    private val supabase = SupabaseManager.client
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::sendImage)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val userId = supabase.auth.currentSessionOrNull()?.user?.id
        if (userId == null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }
        currentUserId = userId

        val selectedUserId = intent.getStringExtra(EXTRA_MATCHED_USER_ID)
        if (selectedUserId.isNullOrBlank()) {
            Log.e(TAG, "Cannot open chat: matchedUserId is missing")
            Toast.makeText(this, "Unable to open this chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        matchedUserId = selectedUserId
        Log.d(TAG, "currentUserId=$currentUserId")
        Log.d(TAG, "matchedUserId=$matchedUserId")

        setContentView(R.layout.activity_chat)

        rvMessages = findViewById(R.id.rv_messages)
        etMessage = findViewById(R.id.et_message)
        btnEmoji = findViewById(R.id.btn_emoji)
        btnImage = findViewById(R.id.btn_image)
        btnSend = findViewById(R.id.btn_send)
        emojiPanel = findViewById(R.id.emoji_panel)
        emojiGrid = findViewById(R.id.emoji_grid)
        btnLogout = findViewById(R.id.btn_logout)

        setupWindowInsets()
        setupEmojiPanel()
        setupRecyclerView()
        loadMessages()
        observeMessages()

        btnEmoji.setOnClickListener { toggleEmojiPanel() }
        btnImage.setOnClickListener { imagePicker.launch("image/*") }

        btnSend.setOnClickListener {
            val content = etMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                sendMessage(content)
            }
        }

        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun setupEmojiPanel() {
        val selectableBackground = TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId
        val itemHeight = (48 * resources.displayMetrics.density).toInt()

        COMMON_EMOJIS.forEach { emoji ->
            val emojiView = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                contentDescription = emoji
                if (selectableBackground != 0) {
                    setBackgroundResource(selectableBackground)
                }
                setOnClickListener { insertEmoji(emoji) }
            }
            emojiGrid.addView(
                emojiView,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = itemHeight
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            )
        }
    }

    private fun toggleEmojiPanel() {
        emojiPanel.visibility = if (emojiPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun insertEmoji(emoji: String) {
        val editable = etMessage.text
        val cursorPosition = etMessage.selectionStart.takeIf { it >= 0 } ?: editable.length
        editable.insert(cursorPosition, emoji)
        etMessage.setSelection(cursorPosition + emoji.length)
        etMessage.requestFocus()
    }

    private fun setupWindowInsets() {
        val root = findViewById<View>(R.id.chat_root)
        val inputLayout = findViewById<View>(R.id.layout_input)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            val bottomMargin = maxOf(imeBottom, systemBars.bottom)
            val layoutParams = inputLayout.layoutParams as ViewGroup.MarginLayoutParams
            if (layoutParams.bottomMargin != bottomMargin) {
                layoutParams.bottomMargin = bottomMargin
                inputLayout.layoutParams = layoutParams
            }

            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(currentUserId)
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            try {
                val messages = supabase.postgrest["messages"]
                    .select {
                        filter {
                            or {
                                and {
                                    eq("sender_id", currentUserId)
                                    eq("receiver_id", matchedUserId)
                                }
                                and {
                                    eq("sender_id", matchedUserId)
                                    eq("receiver_id", currentUserId)
                                }
                            }
                        }
                    }
                    .decodeList<Message>()
                    .sortedWith(
                        compareBy<Message> { parseMessageInstant(it.created_at) ?: Instant.EPOCH }
                            .thenBy { it.id ?: Long.MIN_VALUE }
                    )

                Log.d(TAG, "loaded message count=${messages.size}")
                val senderNames = loadSenderNames(messages.map { it.sender_id }.distinct())
                adapter.submitList(messages, senderNames)
                if (messages.isNotEmpty()) {
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadSenderNames(senderIds: List<String>): Map<String, String> {
        if (senderIds.isEmpty()) return emptyMap()

        return try {
            supabase.postgrest["profiles"]
                .select(columns = Columns.list("id", "full_name")) {
                    filter {
                        isIn("id", senderIds)
                    }
                }
                .decodeList<Profile>()
                .mapNotNull { profile ->
                    profile.full_name
                        ?.takeIf { it.isNotBlank() }
                        ?.let { profile.id to it }
                }
                .toMap()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to load sender profile names", error)
            emptyMap()
        }
    }

    private fun observeMessages() {
        val channel = supabase.realtime.channel("chat")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }

        changeFlow.onEach {
            loadMessages()
        }.launchIn(lifecycleScope)

        lifecycleScope.launch {
            channel.subscribe()
        }
    }

    private fun parseMessageInstant(createdAt: String?): Instant? {
        if (createdAt.isNullOrBlank()) return null
        return runCatching { Instant.parse(createdAt) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
    }

    private fun sendMessage(content: String) {
        lifecycleScope.launch {
            try {
                val message = Message(
                    content = content,
                    sender_id = currentUserId,
                    receiver_id = matchedUserId,
                    message_type = MESSAGE_TYPE_TEXT
                )
                supabase.postgrest["messages"].insert(message)
                Log.d(TAG, "MESSAGE SENT SUCCESS senderId=$currentUserId receiver_id=$matchedUserId")
                etMessage.text.clear()
                Log.d(TAG, "MESSAGE RELOAD AFTER SEND")
                loadMessages()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Failed to send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            try {
                supabase.auth.signOut()
                val intent = Intent(this@ChatActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Logout failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendImage(uri: Uri) {
        lifecycleScope.launch {
            btnImage.isEnabled = false
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected image")
                val extension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(contentResolver.getType(uri))
                    ?: "jpg"
                val imagePath = "$currentUserId/${UUID.randomUUID()}.$extension"
                val bucket = supabase.storage[CHAT_IMAGES_BUCKET]

                bucket.upload(imagePath, bytes)
                val imageUrl = bucket.publicUrl(imagePath)
                val message = Message(
                    content = "",
                    sender_id = currentUserId,
                    receiver_id = matchedUserId,
                    image_url = imageUrl,
                    message_type = MESSAGE_TYPE_IMAGE
                )
                supabase.postgrest["messages"].insert(message)
                Log.d(TAG, "IMAGE MESSAGE SENT receiver_id=$matchedUserId imagePath=$imagePath")
                loadMessages()
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "IMAGE MESSAGE FAILED receiver_id=$matchedUserId message=${error.message}",
                    error
                )
                Toast.makeText(
                    this@ChatActivity,
                    "Could not send image. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                btnImage.isEnabled = true
            }
        }
    }

    companion object {
        const val EXTRA_MATCHED_USER_ID = "matched_user_id"
        private const val TAG = "ChatActivity"
        private const val CHAT_IMAGES_BUCKET = "chat-images"
        private const val MESSAGE_TYPE_TEXT = "text"
        private const val MESSAGE_TYPE_IMAGE = "image"
        private val COMMON_EMOJIS = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅",
            "😂", "🤣", "😊", "😇", "🙂", "🙃",
            "😉", "😌", "😍", "🥰", "😘", "😗",
            "😙", "😚", "😋", "😛", "😝", "😜",
            "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟",
            "😕", "🙁", "☹️", "😣", "😖", "😫",
            "😩", "🥺", "😢", "😭", "😤", "😠",
            "😡", "🤬", "🤯", "😳", "🥵", "🥶",
            "😱", "😨", "😰", "😥", "😓", "🤗",
            "🤔", "🤭", "🤫", "🤥", "😶", "😐",
            "😑", "😬", "🙄", "😯", "😦", "😧",
            "😮", "😲", "🥱", "😴", "🤤", "😪",
            "😵", "🤐", "🤢", "🤮", "🤧", "😷",
            "🤒", "🤕", "👍", "👎", "👌", "✌️",
            "🤞", "🤟", "🤘", "🤙", "👋", "👏",
            "🙌", "👐", "🤲", "🙏", "💪", "🫶",
            "❤️", "🧡", "💛", "💚", "💙", "💜",
            "🖤", "🤍", "🤎", "💔", "💕", "💞",
            "💓", "💗", "💖", "💘", "💝", "💟",
            "🔥", "✨", "⭐", "🌟", "💫", "🎉",
            "🎊", "🎁", "🌹", "🌸", "🌺", "🌻",
            "🍀", "🍓", "🍒", "🍕", "🍰", "☕",
            "🍷", "🥂", "🎵", "🎶", "💯", "💋"
        )
    }
}
