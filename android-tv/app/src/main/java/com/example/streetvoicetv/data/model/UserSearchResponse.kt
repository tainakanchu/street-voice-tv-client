package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSearchResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<UserSearchResult>,
)

@Serializable
data class UserSearchResult(
    val id: Int,
    val username: String,
    val nickname: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val introduction: String? = null,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("songs_count") val songsCount: Int = 0,
)
