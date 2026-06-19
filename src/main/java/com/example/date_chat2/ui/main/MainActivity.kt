package com.example.date_chat2.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.date_chat2.R
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.auth.EmailLoginActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private val supabase = SupabaseManager.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkSessionAndNavigate()

        supabase.handleDeeplinks(intent) {
            checkSessionAndNavigate()
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<AppCompatButton>(R.id.btn_email).setOnClickListener {
            startActivity(Intent(this, EmailLoginActivity::class.java))
        }
    }

    private fun checkSessionAndNavigate() {
        lifecycleScope.launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session?.user != null) {
                startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        supabase.handleDeeplinks(intent) {
            checkSessionAndNavigate()
        }
    }
}
