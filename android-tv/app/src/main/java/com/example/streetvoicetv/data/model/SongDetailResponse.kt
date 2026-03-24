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
    @SerialName("lyrics_is_lrc") val lyricsIsLrc: Boolean = false,
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
    val profile: SongDetailUserProfile? = null,
)

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
