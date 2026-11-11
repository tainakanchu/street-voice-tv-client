package com.example.streetvoicetv.domain.model

data class Artist(
    val id: Int,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?,
    val coverImageUrl: String?,
    val introduction: String?,
    val followersCount: Int,
    val followingCount: Int,
    val songsCount: Int,
    /**
     * Whether the logged-in user follows this artist as reported by the server (`is_follow`);
     * null when the response does not say. Without a session it is always false.
     */
    val isFollowing: Boolean? = null,
)
