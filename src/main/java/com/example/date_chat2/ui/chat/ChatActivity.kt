package com.example.date_chat2.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.util.TypedValue
import android.webkit.MimeTypeMap
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.date_chat2.R
import com.example.date_chat2.data.Message
import com.example.date_chat2.data.model.Profile
import com.example.date_chat2.data.repository.NotificationRepository
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.MainActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnEmoji: Button
    private lateinit var btnImage: Button
    private lateinit var btnVideo: Button
    private lateinit var btnLocation: Button
    private lateinit var btnAttachments: ImageButton
    private lateinit var btnSend: ImageButton
    private lateinit var attachmentPanel: View
    private lateinit var emojiPanel: View
    private lateinit var emojiGrid: GridLayout
    private lateinit var chatAvatar: ImageView
    private lateinit var chatName: TextView
    private lateinit var chatStatus: TextView
    private lateinit var currentUserId: String
    private lateinit var matchedUserId: String
    private var messagePollingJob: Job? = null
    private var isLoadingMessages = false
    private var lastMessageCount = -1
    private var lastMessageId: Long? = null
    
    private val supabase = SupabaseManager.client
    private val notificationRepository = NotificationRepository()
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::sendImage)
    }
    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::handleSelectedVideo)
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            shareCurrentLocation()
        } else {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
        }
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
        btnVideo = findViewById(R.id.btn_video)
        btnLocation = findViewById(R.id.btn_location)
        btnAttachments = findViewById(R.id.btn_attachments)
        btnSend = findViewById(R.id.btn_send)
        attachmentPanel = findViewById(R.id.attachment_panel)
        emojiPanel = findViewById(R.id.emoji_panel)
        emojiGrid = findViewById(R.id.emoji_grid)
        chatAvatar = findViewById(R.id.iv_chat_avatar)
        chatName = findViewById(R.id.tv_chat_name)
        chatStatus = findViewById(R.id.tv_chat_status)

        setupWindowInsets()
        setupChatHeader()
        setupEmojiPanel()
        setupRecyclerView()
        markConversationRead()
        loadMessages()
        observeMessages()

        btnAttachments.setOnClickListener { toggleAttachmentPanel() }
        btnEmoji.setOnClickListener {
            closeAttachmentPanel()
            toggleEmojiPanel()
        }
        btnImage.setOnClickListener {
            closeAttachmentPanel()
            imagePicker.launch("image/*")
        }
        btnVideo.setOnClickListener {
            closeAttachmentPanel()
            videoPicker.launch("video/*")
        }
        btnLocation.setOnClickListener {
            closeAttachmentPanel()
            requestAndShareLocation()
        }

        btnSend.setOnClickListener {
            val content = etMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                sendMessage(content)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        if (::currentUserId.isInitialized && ::matchedUserId.isInitialized && ::adapter.isInitialized) {
            loadMessages()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::currentUserId.isInitialized && ::matchedUserId.isInitialized && ::adapter.isInitialized) {
            startMessagePolling()
        }
    }

    override fun onStop() {
        stopMessagePolling()
        super.onStop()
    }

    override fun onDestroy() {
        stopMessagePolling()
        super.onDestroy()
    }

    private fun setupChatHeader() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        lifecycleScope.launch {
            try {
                val profile = supabase.postgrest["profiles"]
                    .select(
                        columns = Columns.list(
                            "id",
                            "full_name",
                            "avatar_url",
                            "is_online",
                            "last_seen"
                        )
                    ) {
                        filter { eq("id", matchedUserId) }
                    }
                    .decodeSingle<ChatHeaderProfile>()

                chatName.text = profile.full_name?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.unknown_name)
                val statusRes = if (profile.is_online == true) {
                    R.string.online
                } else {
                    R.string.last_seen_recently
                }
                chatStatus.setText(statusRes)
                chatStatus.setTextColor(
                    ContextCompat.getColor(
                        this@ChatActivity,
                        if (profile.is_online == true) R.color.green_like else R.color.text_secondary
                    )
                )
                Glide.with(this@ChatActivity)
                    .load(profile.avatar_url)
                    .placeholder(R.drawable.bg_avatar_placeholder)
                    .error(R.drawable.bg_avatar_placeholder)
                    .centerCrop()
                    .into(chatAvatar)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to load chat header", error)
                chatName.setText(R.string.unknown_name)
                chatStatus.setText(R.string.last_seen_recently)
            }
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

    private fun toggleAttachmentPanel() {
        val showPanel = attachmentPanel.visibility != View.VISIBLE
        attachmentPanel.visibility = if (showPanel) View.VISIBLE else View.GONE
        btnAttachments.setImageResource(
            if (showPanel) R.drawable.ic_close else R.drawable.ic_add_circle
        )
        btnAttachments.contentDescription = getString(
            if (showPanel) R.string.close_attachments else R.string.attachments
        )
    }

    private fun closeAttachmentPanel() {
        attachmentPanel.visibility = View.GONE
        btnAttachments.setImageResource(R.drawable.ic_add_circle)
        btnAttachments.contentDescription = getString(R.string.attachments)
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
        if (isLoadingMessages) return
        isLoadingMessages = true
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
                val newestMessageId = messages.lastOrNull()?.id
                val messagesChanged = messages.size != lastMessageCount ||
                    newestMessageId != lastMessageId
                if (!messagesChanged) return@launch

                val shouldScrollToBottom = isUserNearBottom()
                val senderNames = loadSenderNames(messages.map { it.sender_id }.distinct())
                adapter.submitList(messages, senderNames)
                lastMessageCount = messages.size
                lastMessageId = newestMessageId
                if (messages.isNotEmpty() && shouldScrollToBottom) {
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages", e)
            } finally {
                isLoadingMessages = false
            }
        }
    }

    private fun isUserNearBottom(): Boolean {
        if (adapter.itemCount == 0) return true
        val layoutManager = rvMessages.layoutManager as? LinearLayoutManager ?: return true
        return layoutManager.findLastVisibleItemPosition() >=
            adapter.itemCount - NEAR_BOTTOM_THRESHOLD
    }

    private fun startMessagePolling() {
        if (messagePollingJob?.isActive == true) return
        messagePollingJob = lifecycleScope.launch {
            while (isActive) {
                loadMessages()
                delay(MESSAGE_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopMessagePolling() {
        messagePollingJob?.cancel()
        messagePollingJob = null
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
        val channel = supabase.realtime.channel("chat-$currentUserId-$matchedUserId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }

        changeFlow.onEach { insert ->
            Log.d(TAG, "realtime insert received")
            val message = insert.decodeRecord<Message>()
            val belongsToCurrentConversation =
                (message.sender_id == currentUserId && message.receiver_id == matchedUserId) ||
                    (message.sender_id == matchedUserId && message.receiver_id == currentUserId)
            Log.d(
                TAG,
                "realtime insert belongsToCurrentConversation=$belongsToCurrentConversation"
            )
            if (belongsToCurrentConversation) {
                markConversationRead()
                Log.d(TAG, "reload triggered by realtime")
                loadMessages()
            }
        }.launchIn(lifecycleScope)

        lifecycleScope.launch {
            try {
                channel.subscribe()
                Log.d(TAG, "realtime subscribed")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to subscribe to chat realtime", error)
            }
        }
    }

    private fun markConversationRead() {
        lifecycleScope.launch {
            try {
                notificationRepository.markConversationRead(currentUserId, matchedUserId)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to mark conversation as read", error)
            }
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
                emojiPanel.visibility = View.GONE
                Log.d(TAG, "MESSAGE RELOAD AFTER SEND")
                loadMessages()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Failed to send", Toast.LENGTH_SHORT).show()
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

    private fun handleSelectedVideo(uri: Uri) {
        val sizeBytes = getContentSize(uri)
        val durationMs = getVideoDuration(uri)
        Log.d(TAG, "VIDEO SELECTED uri=$uri sizeBytes=$sizeBytes durationMs=$durationMs")

        when {
            sizeBytes < 0 -> Toast.makeText(this, R.string.video_read_failed, Toast.LENGTH_SHORT).show()
            sizeBytes > MAX_VIDEO_SIZE_BYTES -> Toast.makeText(
                this,
                R.string.video_too_large,
                Toast.LENGTH_LONG
            ).show()
            durationMs == null -> Toast.makeText(this, R.string.video_read_failed, Toast.LENGTH_SHORT).show()
            durationMs > MAX_VIDEO_DURATION_MS -> Toast.makeText(
                this,
                R.string.video_too_long,
                Toast.LENGTH_LONG
            ).show()
            else -> sendVideo(uri)
        }
    }

    private fun getContentSize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeColumn >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeColumn)) {
                return cursor.getLong(sizeColumn)
            }
        }
        return runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun getVideoDuration(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (error: Exception) {
            Log.e(TAG, "VIDEO METADATA FAILED uri=$uri", error)
            null
        } finally {
            retriever.release()
        }
    }

    private fun sendVideo(uri: Uri) {
        lifecycleScope.launch {
            btnVideo.isEnabled = false
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected video")
                val extension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(contentResolver.getType(uri))
                    ?: "mp4"
                val videoPath = "videos/$currentUserId/${UUID.randomUUID()}.$extension"
                val bucket = supabase.storage[CHAT_IMAGES_BUCKET]

                try {
                    bucket.upload(videoPath, bytes)
                    Log.d(TAG, "VIDEO UPLOAD SUCCESS path=$videoPath")
                } catch (error: Exception) {
                    Log.e(TAG, "VIDEO UPLOAD FAILURE path=$videoPath", error)
                    throw error
                }

                val videoUrl = bucket.publicUrl(videoPath)
                val message = Message(
                    content = "",
                    sender_id = currentUserId,
                    receiver_id = matchedUserId,
                    image_url = videoUrl,
                    message_type = MESSAGE_TYPE_VIDEO
                )
                try {
                    supabase.postgrest["messages"].insert(message)
                    Log.d(TAG, "VIDEO MESSAGE INSERT SUCCESS receiver_id=$matchedUserId")
                } catch (error: Exception) {
                    Log.e(TAG, "VIDEO MESSAGE INSERT FAILURE receiver_id=$matchedUserId", error)
                    throw error
                }
                loadMessages()
            } catch (error: Exception) {
                Toast.makeText(this@ChatActivity, R.string.video_send_failed, Toast.LENGTH_SHORT).show()
            } finally {
                btnVideo.isEnabled = true
            }
        }
    }

    private fun requestAndShareLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            shareCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun shareCurrentLocation() {
        val locationManager = getSystemService(LocationManager::class.java)
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            hasFineLocation && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                LocationManager.GPS_PROVIDER
            }
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
                LocationManager.NETWORK_PROVIDER
            }
            else -> null
        }

        if (provider == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        btnLocation.isEnabled = false
        try {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                CancellationSignal(),
                ContextCompat.getMainExecutor(this)
            ) { location ->
                if (location == null) {
                    btnLocation.isEnabled = true
                    Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show()
                    return@getCurrentLocation
                }
                val mapsLink = String.format(
                    Locale.US,
                    "https://maps.google.com/?q=%.6f,%.6f",
                    location.latitude,
                    location.longitude
                )
                sendLocationMessage(mapsLink)
            }
        } catch (error: SecurityException) {
            btnLocation.isEnabled = true
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            btnLocation.isEnabled = true
            Log.e(TAG, "LOCATION LOOKUP FAILED", error)
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendLocationMessage(mapsLink: String) {
        lifecycleScope.launch {
            try {
                val message = Message(
                    content = mapsLink,
                    sender_id = currentUserId,
                    receiver_id = matchedUserId,
                    message_type = MESSAGE_TYPE_LOCATION
                )
                supabase.postgrest["messages"].insert(message)
                loadMessages()
            } catch (error: Exception) {
                Log.e(TAG, "LOCATION MESSAGE FAILED receiver_id=$matchedUserId", error)
                Toast.makeText(
                    this@ChatActivity,
                    R.string.location_send_failed,
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                btnLocation.isEnabled = true
            }
        }
    }

    companion object {
        const val EXTRA_MATCHED_USER_ID = "matched_user_id"
        private const val TAG = "ChatActivity"
        private const val CHAT_IMAGES_BUCKET = "chat-images"
        private const val MESSAGE_TYPE_TEXT = "text"
        private const val MESSAGE_TYPE_IMAGE = "image"
        private const val MESSAGE_TYPE_VIDEO = "video"
        private const val MESSAGE_TYPE_LOCATION = "location"
        private const val MESSAGE_POLL_INTERVAL_MS = 2_000L
        private const val NEAR_BOTTOM_THRESHOLD = 3
        private const val MAX_VIDEO_DURATION_MS = 30_000L
        private const val MAX_VIDEO_SIZE_BYTES = 20L * 1024L * 1024L
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

@Serializable
private data class ChatHeaderProfile(
    val id: String,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val is_online: Boolean? = null,
    val last_seen: String? = null
)
