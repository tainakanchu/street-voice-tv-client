package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/v4/song/genre/ → `{results: [{id, genre}]}` (22 genres, id 0 = 未分類, no slug). */
@Serializable
data class GenreListResponse(
    val results: List<GenreResult> = emptyList(),
)

@Serializable
data class GenreResult(
    val id: Int,
    val genre: String,
)

/** GET /api/v4/user/{username}/following/ (public). */
@Serializable
data class FollowingResponse(
    val count: Int? = null,
    val offset: Int = 0,
    val limit: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<FollowingEntry>,
)

@Serializable
data class FollowingEntry(
    @SerialName("to_user") val toUser: FollowingUser,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class FollowingUser(
    val id: Int = 0,
    val username: String,
    val profile: FollowingUserProfile? = null,
)

@Serializable
data class FollowingUserProfile(
    val nickname: String? = null,
    val image: String? = null,
    @SerialName("is_follow") val isFollow: Boolean? = null,
)
