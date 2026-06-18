package com.example.date_chat2.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseManager {

    private const val SUPABASE_URL = "https://dzzrderoixqzczvfapvx.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_CLI9lZSs6X6iB6WCSqcRyw_Sc-2U58k"

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
