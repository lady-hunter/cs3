package com.example.date_chat2.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MatchRow(
    val id: Long? = null,
    @SerialName("user1_id")
    val user1Id: String,
    @SerialName("user2_id")
    val user2Id: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

data class MatchItem(
    val matchId: Long,
    val otherUserId: String,
    val profile: Profile
)
