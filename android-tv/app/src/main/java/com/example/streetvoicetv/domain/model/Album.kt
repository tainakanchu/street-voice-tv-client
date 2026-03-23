package com.example.streetvoicetv.domain.model

data class Album(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val songsCount: Int,
    val artistName: String?,
    val artistUsername: String?,
    val createdAt: String?,
)
