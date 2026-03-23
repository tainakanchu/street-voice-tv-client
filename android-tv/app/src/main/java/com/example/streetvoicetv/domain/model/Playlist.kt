package com.example.streetvoicetv.domain.model

data class Playlist(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val description: String? = null,
    val songsCount: Int,
    val playsCount: Int = 0,
    val likesCount: Int = 0,
    val curatorName: String?,
    val createdAt: String? = null,
)
