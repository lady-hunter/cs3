package com.example.date_chat2.data.repository

import com.example.date_chat2.data.model.MatchRow
import com.example.date_chat2.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

class NotificationRepository {

    private val client = SupabaseManager.client

    suspend fun getUnreadMessageCounts(userId: String): Map<String, Int> {
        return client.postgrest["messages"]
            .select(columns = Columns.list("sender_id")) {
                filter {
                    eq("receiver_id", userId)
                    eq("is_read", false)
                }
            }
            .decodeList<UnreadMessage>()
            .groupingBy { it.senderId }
            .eachCount()
    }

    suspend fun markConversationRead(userId: String, otherUserId: String) {
        client.postgrest["messages"].update(mapOf("is_read" to true)) {
            filter {
                eq("receiver_id", userId)
                eq("sender_id", otherUserId)
                eq("is_read", false)
            }
        }
    }

    suspend fun getMatchIds(userId: String): Set<Long> {
        val columns = Columns.list("id", "user1_id", "user2_id", "created_at")
        val byUser1 = client.postgrest["matches"]
            .select(columns = columns) {
                filter { eq("user1_id", userId) }
            }
            .decodeList<MatchRow>()
        val byUser2 = client.postgrest["matches"]
            .select(columns = columns) {
                filter { eq("user2_id", userId) }
            }
            .decodeList<MatchRow>()

        return (byUser1 + byUser2).mapNotNull { it.id }.toSet()
    }

    @Serializable
    private data class UnreadMessage(
        @kotlinx.serialization.SerialName("sender_id")
        val senderId: String
    )
}
