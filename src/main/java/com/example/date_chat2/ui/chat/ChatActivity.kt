package com.example.date_chat2.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.date_chat2.R
import com.example.date_chat2.data.Message
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.MainActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
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
    private lateinit var btnSend: Button
    private lateinit var btnLogout: Button
    private lateinit var currentUserId: String
    
    private val supabase = SupabaseManager.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        btnSend = findViewById(R.id.btn_send)
        btnLogout = findViewById(R.id.btn_logout)

        setupRecyclerView()
        loadMessages()
        observeMessages()

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
                
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                etMessage.text.clear()
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
}
