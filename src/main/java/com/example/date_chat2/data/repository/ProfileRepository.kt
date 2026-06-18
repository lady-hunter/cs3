package com.example.date_chat2.data.repository

import android.util.Log
import com.example.date_chat2.data.model.MatchItem
import com.example.date_chat2.data.model.MatchRow
import com.example.date_chat2.data.model.Profile
import com.example.date_chat2.data.model.ProfileUpdate
import com.example.date_chat2.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable

@Serializable
private data class SwipeAction(
    val user_id: String,
    val target_user_id: String,
    val action: String
)

@Serializable
private data class MatchInsert(
    val user1_id: String,
    val user2_id: String
)

class DuplicateSwipeException : Exception("This profile was already swiped.")

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
            Log.e(TAG, "PROFILE LOAD FAILED userId=$userId message=${e.message}", e)
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
            Log.e(TAG, "AVATAR UPLOAD FAILED userId=$userId message=${e.message}", e)
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
                .select(columns = PROFILE_COLUMNS) {
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
            Log.e(TAG, "SWIPE PROFILE LOAD FAILED currentUserId=$userId message=${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun upsertProfile(profileUpdate: ProfileUpdate): Boolean {
        return try {
            client.postgrest["profiles"].upsert(profileUpdate)
            true
        } catch (e: Exception) {
            Log.e(
                TAG,
                "PROFILE UPSERT FAILED userId=${profileUpdate.id} message=${e.message}",
                e
            )
            false
        }
    }

    suspend fun saveSwipeAction(
        userId: String,
        targetUserId: String,
        action: String
    ): Result<Unit> {
        require(action == "like" || action == "skip")

        if (userId == targetUserId) {
            Log.e(TAG, "SWIPE INSERT REJECTED user cannot swipe self userId=$userId action=$action")
            return Result.failure(IllegalArgumentException("User cannot swipe themselves."))
        }

        Log.d(TAG, "SWIPE INSERT REQUEST userId=$userId targetUserId=$targetUserId action=$action")

        return try {
            val response = client.postgrest["swipes"].insert(
                SwipeAction(
                    user_id = userId,
                    target_user_id = targetUserId,
                    action = action
                )
            )
            Log.d(
                TAG,
                "SWIPE INSERT SUCCESS userId=$userId targetUserId=$targetUserId action=$action response=$response"
            )

            if (action == "like") {
                runCatching {
                    createMatchIfMutualLike(userId, targetUserId)
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "MATCH CHECK/CREATE FAILED currentUserId=$userId targetUserId=$targetUserId message=${error.message}",
                        error
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            val failure = if (e.isDuplicateKeyViolation()) DuplicateSwipeException() else e
            Log.e(
                TAG,
                "SWIPE INSERT FAILED userId=$userId targetUserId=$targetUserId action=$action message=${e.message}",
                e
            )
            Result.failure(failure)
        }
    }

    private fun Exception.isDuplicateKeyViolation(): Boolean {
        val errorText = message.orEmpty()
        return errorText.contains("23505") ||
            errorText.contains("duplicate key", ignoreCase = true) ||
            errorText.contains("unique constraint", ignoreCase = true)
    }

    suspend fun getMatchesForUser(userId: String): Result<List<MatchItem>> {
        return try {
            val matchRows = loadMatchRows(userId)
            val items = matchRows.mapNotNull { match ->
                val otherUserId = if (match.user1Id == userId) match.user2Id else match.user1Id
                val profile = getProfile(otherUserId)
                if (profile == null) {
                    Log.d(
                        TAG,
                        "MATCH PROFILE SKIPPED missing profile for otherUserId=$otherUserId matchId=${match.id}"
                    )
                    null
                } else {
                    Log.d(
                        TAG,
                        "MATCH PROFILE LOADED currentUserId=$userId otherUserId=$otherUserId avatarUrl=${profile.avatar_url}"
                    )
                    MatchItem(
                        matchId = match.id ?: -1L,
                        otherUserId = otherUserId,
                        profile = profile
                    )
                }
            }
            Log.d(TAG, "MATCH LOAD SUCCESS currentUserId=$userId count=${items.size}")
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "MATCH LOAD FAILED currentUserId=$userId message=${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun createMatchIfMutualLike(currentUserId: String, targetUserId: String) {
        if (currentUserId == targetUserId) {
            Log.d(TAG, "MATCH CREATE skipped because user cannot match with self userId=$currentUserId")
            return
        }

        val pair = normalizePair(currentUserId, targetUserId)
        Log.d(
            TAG,
            "MATCH CHECK currentUserId=$currentUserId targetUserId=$targetUserId normalizedUser1=${pair.first} normalizedUser2=${pair.second}"
        )

        val existingMatch = client.postgrest["matches"]
            .select {
                filter {
                    eq("user1_id", pair.first)
                    eq("user2_id", pair.second)
                }
            }
            .decodeSingleOrNull<MatchRow>()

        if (existingMatch != null) {
            Log.d(
                TAG,
                "MATCH CHECK mutualLikeFound=true matchCreated=false reason=existing_match matchId=${existingMatch.id}"
            )
            return
        }

        val reciprocalLikeExists = client.postgrest["swipes"]
            .select {
                filter {
                    eq("user_id", targetUserId)
                    eq("target_user_id", currentUserId)
                    eq("action", "like")
                }
            }
            .decodeSingleOrNull<SwipeAction>() != null

        Log.d(
            TAG,
            "MATCH CHECK currentUserId=$currentUserId targetUserId=$targetUserId mutualLikeFound=$reciprocalLikeExists"
        )

        if (!reciprocalLikeExists) {
            return
        }

        val response = client.postgrest["matches"].upsert(
            MatchInsert(
                user1_id = pair.first,
                user2_id = pair.second
            )
        ) {
            onConflict = "user1_id,user2_id"
            ignoreDuplicates = true
        }
        Log.d(
            TAG,
            "MATCH CREATE currentUserId=$currentUserId targetUserId=$targetUserId matchCreated=true response=$response"
        )
    }

    private suspend fun loadMatchRows(userId: String): List<MatchRow> {
        val byUser1 = client.postgrest["matches"]
            .select {
                filter {
                    eq("user1_id", userId)
                }
            }
            .decodeList<MatchRow>()

        val byUser2 = client.postgrest["matches"]
            .select {
                filter {
                    eq("user2_id", userId)
                }
            }
            .decodeList<MatchRow>()

        return (byUser1 + byUser2)
            .distinctBy { normalizePair(it.user1Id, it.user2Id) }
    }

    private fun normalizePair(userA: String, userB: String): Pair<String, String> {
        return if (userA <= userB) userA to userB else userB to userA
    }

    private companion object {
        const val TAG = "ProfileRepository"
        val PROFILE_COLUMNS = Columns.list(
            "id",
            "full_name",
            "avatar_url",
            "gender",
            "birth_date",
            "bio"
        )
    }
}
