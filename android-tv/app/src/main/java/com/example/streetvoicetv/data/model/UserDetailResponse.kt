package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/v4/user/{username}/
 *
 * The live API nests everything but id / username under `profile`
 * (`{id, username, profile: {nickname, image, cover_image, introduction, follower_count,
 * following_count, songs_count, is_follow}}`); a flat shape is accepted as well.
 */
@Serializable
data class UserDetailResponse(
    val id: Int,
    val username: String,
    val nickname: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val introduction: String? = null,
    @SerialName("followers_count") val followersCount: Int? = null,
    @SerialName("following_count") val followingCount: Int? = null,
    @SerialName("songs_count") val songsCount: Int? = null,
    val profile: UserDetailProfile? = null,
)

@Serializable
data class UserDetailProfile(
    val nickname: String? = null,
    val image: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val introduction: String? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("following_count") val followingCount: Int? = null,
    @SerialName("songs_count") val songsCount: Int? = null,
    /** Whether the logged-in user follows this user (always false without a session). */
    @SerialName("is_follow") val isFollow: Boolean? = null,
)
