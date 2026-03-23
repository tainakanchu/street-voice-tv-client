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
)
