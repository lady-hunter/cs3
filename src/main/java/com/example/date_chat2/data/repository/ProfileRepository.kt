package com.example.date_chat2.data.repository

import com.example.date_chat2.data.model.Profile
import com.example.date_chat2.data.model.ProfileUpdate
import com.example.date_chat2.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns

class ProfileRepository {
    private val client = SupabaseManager.client

    suspend fun getProfile(userId: String): Profile? {
        return try {
            client.postgrest["profiles"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadAvatar(userId: String, bytes: ByteArray): String? {
        return try {
            val fileName = "$userId/${System.currentTimeMillis()}.jpg"
            val bucket = client.storage["avatars"]
            bucket.upload(fileName, bytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getProfilesForSwiping(userId: String, limit: Int = 20): Result<List<Profile>> {
        return try {
            val swipedUserIds = client.postgrest["swipes"]
                .select(columns = Columns.list("target_user_id")) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<Map<String, String>>()
                .mapNotNull { it["target_user_id"] }

            val profiles = client.postgrest["profiles"]
                .select {
                    filter {
                        neq("id", userId)
                        if (swipedUserIds.isNotEmpty()) {
                            and(negate = true) {
                                isIn("id", swipedUserIds)
                            }
                        }
                    }
                    limit(limit.toLong())
                    order("id", Order.ASCENDING) 
                }.decodeList<Profile>()
            Result.success(profiles)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun upsertProfile(profileUpdate: ProfileUpdate): Boolean {
        return try {
            // Sử dụng upsert để tự động tạo mới nếu chưa có hoặc cập nhật nếu đã tồn tại dựa trên ID
            client.postgrest["profiles"].upsert(profileUpdate)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
