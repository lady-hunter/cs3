package com.example.date_chat2.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String? = null,
    val username: String? = null,
    val created_at: String? = null
)
