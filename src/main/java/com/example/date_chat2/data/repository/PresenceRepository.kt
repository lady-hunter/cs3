package com.example.date_chat2.data.repository

import android.util.Log
import com.example.date_chat2.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
private data class OnlinePresenceUpdate(
    val is_online: Boolean
)

@Serializable
private data class LastSeenPresenceUpdate(
    val last_seen: String
)

@Serializable
private data class OfflinePresenceUpdate(
    val is_online: Boolean,
    val last_seen: String
)

class PresenceRepository {
    private val client = SupabaseManager.client

    suspend fun markOnline(userId: String) {
        updatePresence(userId, "online") {
            client.postgrest["profiles"].update(OnlinePresenceUpdate(is_online = true)) {
                filter { eq("id", userId) }
            }
        }
    }

    suspend fun updateLastSeen(userId: String) {
        updatePresence(userId, "last_seen") {
            client.postgrest["profiles"].update(
                LastSeenPresenceUpdate(last_seen = Instant.now().toString())
            ) {
                filter { eq("id", userId) }
            }
        }
    }

    suspend fun markOffline(userId: String) {
        updatePresence(userId, "offline") {
            client.postgrest["profiles"].update(
                OfflinePresenceUpdate(
                    is_online = false,
                    last_seen = Instant.now().toString()
                )
            ) {
                filter { eq("id", userId) }
            }
        }
    }

    private suspend fun updatePresence(
        userId: String,
        status: String,
        update: suspend () -> Unit
    ) {
        try {
            update()
            Log.d(TAG, "PRESENCE UPDATE success userId=$userId status=$status")
        } catch (error: Exception) {
            Log.e(
                TAG,
                "PRESENCE UPDATE failed userId=$userId status=$status message=${error.message}",
                error
            )
        }
    }

    private companion object {
        const val TAG = "PresenceRepository"
    }
}
