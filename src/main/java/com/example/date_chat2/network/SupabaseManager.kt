package com.example.date_chat2.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseManager {

    private const val SUPABASE_URL = "https://dzzrderoixqzczvfapvx.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR6enJkZXJvaXhxemN6dmZhcHZ4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE3MDI3NTgsImV4cCI6MjA5NzI3ODc1OH0.wk9kHSy6k_bDHhLRnE1oyn6KJfXZlmMJYuiyQPElXhc"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}
