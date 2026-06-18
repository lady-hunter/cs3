package com.example.date_chat2.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long? = null,
    val created_at: String? = null,
    val content: String,
    val sender_id: String
)
