package com.example.date_chat2

import android.app.Application
import com.example.date_chat2.network.SupabaseManager

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseManager.client
    }
}
