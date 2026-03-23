package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/v4/user/{username}/songs/ のレスポンス */
@Serializable
data class UserSongsResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<UserSongResult>,
)

@Serializable
data class UserSongResult(
    val id: Int,
    val name: String,
    val image: String? = null,
    val length: Double = 0.0,
    val user: SearchSongUser,
    @SerialName("plays_count") val playsCount: Int = 0,
)
