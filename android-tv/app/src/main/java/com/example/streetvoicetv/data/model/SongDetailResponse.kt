package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongDetailResponse(
    val id: Int,
    val name: String,
    val image: String? = null,
    val length: Double = 0.0,
    val genre: Int? = null,
    val synopsis: String? = null,
    val lyrics: String? = null,
    @SerialName("lyrics_is_lrc") val lyricsIsLrc: Boolean? = null,
    @SerialName("plays_count") val playsCount: Int = 0,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("publish_at") val publishAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val user: SongDetailUser,
    val album: SongDetailAlbum? = null,
    @SerialName("is_like") val isLike: Boolean = false,
)

@Serializable
data class SongDetailUser(
    val username: String,
    // v5 returns a flat user ({id, username, nickname, profile_image}, see docs/findings.md);
    // older responses nest these under `profile`. Both shapes are accepted.
    val id: Int? = null,
    val nickname: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
    val profile: SongDetailUserProfile? = null,
) {
    val displayNickname: String?
        get() = nickname?.takeIf { it.isNotBlank() } ?: profile?.nickname?.takeIf { it.isNotBlank() }
    val displayImage: String?
        get() = profileImage?.takeIf { it.isNotBlank() } ?: profile?.image?.takeIf { it.isNotBlank() }
}

@Serializable
data class SongDetailUserProfile(
    val nickname: String? = null,
    val image: String? = null,
)

@Serializable
data class SongDetailAlbum(
    val id: Int = 0,
    val name: String,
    val image: String? = null,
)
