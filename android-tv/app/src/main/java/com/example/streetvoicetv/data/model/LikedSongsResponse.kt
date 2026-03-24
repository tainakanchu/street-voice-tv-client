package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LikedSongsResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<LikedEntry>,
)

@Serializable
data class LikedEntry(
    val id: Int,
    @SerialName("content_object") val contentObject: NestedSong,
)
