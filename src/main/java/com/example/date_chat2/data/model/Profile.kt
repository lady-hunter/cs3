package com.example.date_chat2.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val gender: String? = null,
    val birth_date: String? = null,
)

@Serializable
data class ProfileUpdate(
    val id: String, // Bắt buộc có ID để thực hiện upsert
    val full_name: String? = null,
    val avatar_url: String? = null,
    val gender: String? = null,
    val birth_date: String? = null
)
