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
    val user: SongDetailUser,
    val album: SongDetailAlbum? = null,
)

@Serializable
data class SongDetailUser(
    val username: String,
    val nickname: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
)

@Serializable
data class SongDetailAlbum(
    val name: String,
    val image: String? = null,
)
