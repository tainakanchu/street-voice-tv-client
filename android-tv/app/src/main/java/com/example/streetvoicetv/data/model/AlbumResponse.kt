package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/v4/user/{username}/albums/ のレスポンス */
@Serializable
data class AlbumListResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<AlbumResult>,
)

@Serializable
data class AlbumResult(
    val id: Int,
    val name: String,
    val image: String? = null,
    @SerialName("songs_count") val songsCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val user: AlbumUser? = null,
)

@Serializable
data class AlbumUser(
    val username: String,
    val nickname: String? = null,
)

/** GET /api/v4/album/{id}/songs/ のレスポンス */
@Serializable
data class AlbumSongsResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<AlbumSongResult>,
)

@Serializable
data class AlbumSongResult(
    val id: Int,
    val name: String,
    val image: String? = null,
    val length: Double = 0.0,
    val user: SearchSongUser,
    @SerialName("plays_count") val playsCount: Int = 0,
)

/** GET /api/v4/album/{id}/ のレスポンス */
@Serializable
data class AlbumDetailResponse(
    val id: Int,
    val name: String,
    val image: String? = null,
    @SerialName("songs_count") val songsCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val user: AlbumUser? = null,
)
