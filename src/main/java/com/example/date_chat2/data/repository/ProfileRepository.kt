package com.example.date_chat2.data.repository

import android.util.Log
import com.example.date_chat2.data.model.MatchItem
import com.example.date_chat2.data.model.MatchRow
import com.example.date_chat2.data.model.Profile
import com.example.date_chat2.data.model.ProfileUpdate
import com.example.date_chat2.data.model.SwipeFilterPreferences
import com.example.date_chat2.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import java.time.LocalDate

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

@Serializable
private data class SwipeFilterRow(
    val preferred_gender: String? = null,
    val min_age: Int? = null,
    val max_age: Int? = null
)

@Serializable
private data class SwipeFilterUpdate(
    val preferred_gender: String,
    val min_age: Int,
    val max_age: Int
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

    suspend fun getSwipeFilterPreferences(userId: String): Result<SwipeFilterPreferences> {
        return try {
            val row = client.postgrest["profiles"]
                .select(columns = SWIPE_FILTER_COLUMNS) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<SwipeFilterRow>()

            val preferences = SwipeFilterPreferences(
                preferredGender = row?.preferred_gender
                    ?.lowercase()
                    ?.takeIf { it in ALLOWED_GENDERS }
                    ?: DEFAULT_PREFERRED_GENDER,
                minAge = row?.min_age?.coerceIn(MIN_ALLOWED_AGE, MAX_ALLOWED_AGE)
                    ?: MIN_ALLOWED_AGE,
                maxAge = row?.max_age?.coerceIn(MIN_ALLOWED_AGE, MAX_ALLOWED_AGE)
                    ?: MAX_ALLOWED_AGE
            ).let { preferences ->
                if (preferences.maxAge < preferences.minAge) {
                    preferences.copy(maxAge = preferences.minAge)
                } else {
                    preferences
                }
            }
            Log.d(
                TAG,
                "SWIPE FILTER LOADED userId=$userId preferredGender=${preferences.preferredGender} " +
                    "minAge=${preferences.minAge} maxAge=${preferences.maxAge}"
            )
            Result.success(preferences)
        } catch (e: Exception) {
            Log.e(TAG, "SWIPE FILTER LOAD FAILED userId=$userId message=${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun saveSwipeFilterPreferences(
        userId: String,
        preferences: SwipeFilterPreferences
    ): Result<Unit> {
        require(preferences.preferredGender in ALLOWED_GENDERS)
        require(preferences.minAge >= MIN_ALLOWED_AGE)
        require(preferences.maxAge >= preferences.minAge)
        require(preferences.maxAge <= MAX_ALLOWED_AGE)

        return try {
            client.postgrest["profiles"].update(
                SwipeFilterUpdate(
                    preferred_gender = preferences.preferredGender,
                    min_age = preferences.minAge,
                    max_age = preferences.maxAge
                )
            ) {
                filter { eq("id", userId) }
            }
            Log.d(
                TAG,
                "SWIPE FILTER SAVED userId=$userId preferredGender=${preferences.preferredGender} " +
                    "minAge=${preferences.minAge} maxAge=${preferences.maxAge}"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SWIPE FILTER SAVE FAILED userId=$userId message=${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getProfilesForSwiping(
        userId: String,
        preferences: SwipeFilterPreferences,
        limit: Int = 20
    ): Result<List<Profile>> {
        return try {
            val swipedUserIds = client.postgrest["swipes"]
                .select(columns = Columns.list("target_user_id")) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<Map<String, String>>()
                .mapNotNull { it["target_user_id"] }

            val today = LocalDate.now()
            val oldestBirthDate = today.minusYears(preferences.maxAge.toLong() + 1).plusDays(1)
            val youngestBirthDate = today.minusYears(preferences.minAge.toLong())

            val profiles = client.postgrest["profiles"]
                .select(columns = PROFILE_COLUMNS) {
                    filter {
                        neq("id", userId)
                        if (swipedUserIds.isNotEmpty()) {
                            and(negate = true) {
                                isIn("id", swipedUserIds)
                            }
                        }
                        if (preferences.preferredGender != DEFAULT_PREFERRED_GENDER) {
                            eq("gender", preferences.preferredGender)
                        }
                        gte("birth_date", oldestBirthDate.toString())
                        lte("birth_date", youngestBirthDate.toString())
                    }
                    limit(limit.toLong())
                    order("id", Order.ASCENDING)
                }.decodeList<Profile>()
            Log.d(
                TAG,
                "SWIPE CANDIDATES FILTERED userId=$userId preferredGender=${preferences.preferredGender} " +
                    "minAge=${preferences.minAge} maxAge=${preferences.maxAge} count=${profiles.size}"
            )
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
    ): Result<Boolean> {
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

            val matchCreatedNew = if (action == "like") {
                runCatching {
                    createMatchIfMutualLike(userId, targetUserId)
                }.getOrElse { error ->
                    Log.e(
                        TAG,
                        "MATCH CHECK/CREATE FAILED currentUserId=$userId targetUserId=$targetUserId message=${error.message}",
                        error
                    )
                    false
                }
            } else {
                false
            }
            Log.d(
                TAG,
                "MATCH RESULT currentUserId=$userId targetUserId=$targetUserId matchCreatedNew=$matchCreatedNew"
            )

            Result.success(matchCreatedNew)
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

    private suspend fun createMatchIfMutualLike(
        currentUserId: String,
        targetUserId: String
    ): Boolean {
        if (currentUserId == targetUserId) {
            Log.d(TAG, "MATCH CREATE skipped because user cannot match with self userId=$currentUserId")
            return false
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
            return false
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
            return false
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
        return true
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
            "birth_dateMFWE",
            "bio"
        )
        val SWIPE_FILTER_COLUMNS = Columns.list("preferred_gender", "min_age", "max_age")
        val ALLOWED_GENDERS = setOf("any", "male", "female")
        const val DEFAULT_PREFERRED_GENDER = "any"
        const val MIN_ALLOWED_AGE = 18
        const val MAX_ALLOWED_AGE = 99
    }
}
