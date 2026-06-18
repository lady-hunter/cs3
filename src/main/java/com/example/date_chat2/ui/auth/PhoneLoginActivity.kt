package com.example.date_chat2.ui.auth

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.date_chat2.R

class PhoneLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_login)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnContinue = findViewById<AppCompatButton>(R.id.btn_continue)
        val etPhone = findViewById<EditText>(R.id.et_phone)

        btnBack.setOnClickListener {
            finish()
        }

        btnContinue.setOnClickListener {
            val phone = etPhone.text.toString()
            if (phone.isNotEmpty()) {
                // TODO: Logic for Supabase Login/Signup will go here
            }
        }
    }
}
