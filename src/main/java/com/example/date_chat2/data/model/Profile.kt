package com.example.date_chat2.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val gender: String? = null,
    val birth_date: String? = null,
    val bio: String? = null,
    val preferred_gender: String? = "any",
    val min_age: Int? = 18,
    val max_age: Int? = 99,
)

data class SwipeFilterPreferences(
    val preferredGender: String = "any",
    val minAge: Int = 18,
    val maxAge: Int = 99
)

@Serializable
data class ProfileUpdate(
    val id: String,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val gender: String? = null,
    val birth_date: String? = null,
    val bio: String? = null
)
