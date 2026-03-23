package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 共通: ネストされた user 構造 ---

@Serializable
data class NestedUser(
    val username: String,
    val profile: NestedUserProfile? = null,
)

@Serializable
data class NestedUserProfile(
    val nickname: String? = null,
    val image: String? = null,
)

// --- 共通: ネストされた song 構造 ---

@Serializable
data class NestedSong(
    val id: Int,
    val name: String,
    val image: String? = null,
    val length: Double = 0.0,
    val user: NestedUser,
    @SerialName("plays_count") val playsCount: Int = 0,
)

// --- Chart ---

/** GET /api/v5/chart/realtime/{style}/ */
@Serializable
data class ChartResponse(
    val offset: Int = 0,
    val limit: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<ChartEntry>,
)

@Serializable
data class ChartEntry(
    val id: Int,
    val song: NestedSong,
)

// --- Playlist Section ---

/** GET /api/v4/playlist_section/{id}/playlists/ */
@Serializable
data class SectionPlaylistsResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<PlaylistResult>,
)

@Serializable
data class PlaylistResult(
    val id: Int,
    val name: String,
    val image: String? = null,
    @SerialName("songs_count") val songsCount: Int = 0,
    val user: NestedUser? = null,
)

// --- Playlist Songs ---

/** GET /api/v4/playlist/{id}/songs/ */
@Serializable
data class PlaylistSongsResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<PlaylistSongEntry>,
)

@Serializable
data class PlaylistSongEntry(
    val id: Int,
    val song: NestedSong,
)

// --- Playlist Detail ---

/** GET /api/v4/playlist/{id}/ */
@Serializable
data class PlaylistDetailResponse(
    val id: Int,
    val name: String,
    val image: String? = null,
    val description: String? = null,
    @SerialName("songs_count") val songsCount: Int = 0,
    @SerialName("plays_count") val playsCount: Int = 0,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val user: NestedUser? = null,
)

// --- Editor's Choice ---

/** GET /api/v4/editor_choice/ */
@Serializable
data class EditorChoiceResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<EditorChoiceEntry>,
)

@Serializable
data class EditorChoiceEntry(
    val id: Int,
    @SerialName("content_object") val contentObject: NestedSong,
)
