package com.example.date_chat2.data

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long? = null,
    val created_at: String? = null,
    val content: String,
    val sender_id: String,
    val receiver_id: String,
    val image_url: String? = null,
    val message_type: String = "text",
    val is_read: Boolean = false
)
