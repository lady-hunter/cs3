package com.example.date_chat2.ui.chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnEmoji: Button
    private lateinit var btnSend: Button
    private lateinit var btnLogout: Button
    private lateinit var currentUserId: String
    
    private val supabase = SupabaseManager.client

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

        setContentView(R.layout.activity_chat)

        rvMessages = findViewById(R.id.rv_messages)
        etMessage = findViewById(R.id.et_message)
        btnEmoji = findViewById(R.id.btn_emoji)
        btnSend = findViewById(R.id.btn_send)
        btnLogout = findViewById(R.id.btn_logout)

        setupWindowInsets()
        setupRecyclerView()
        loadMessages()
        observeMessages()

        btnEmoji.setOnClickListener { showEmojiPicker() }

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

    private fun showEmojiPicker() {
        PopupMenu(this, btnEmoji).apply {
            COMMON_EMOJIS.forEachIndexed { index, emoji ->
                menu.add(0, index, index, emoji)
            }
            setOnMenuItemClickListener { item ->
                val emoji = COMMON_EMOJIS[item.itemId]
                val cursorPosition = etMessage.selectionStart.coerceAtLeast(0)
                etMessage.text.insert(cursorPosition, emoji)
                etMessage.requestFocus()
                true
            }
            show()
        }
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
                    .select()
                    .decodeList<Message>()
                    .sortedBy { it.id }

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

    private fun sendMessage(content: String) {
        lifecycleScope.launch {
            try {
                val message = Message(
                    content = content,
                    sender_id = currentUserId
                )
                supabase.postgrest["messages"].insert(message)
                Log.d(TAG, "MESSAGE SENT SUCCESS senderId=$currentUserId")
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

    private companion object {
        const val TAG = "ChatActivity"
        val COMMON_EMOJIS = listOf("❤️", "😂", "😊", "😍", "👍", "🎉")
    }
}
