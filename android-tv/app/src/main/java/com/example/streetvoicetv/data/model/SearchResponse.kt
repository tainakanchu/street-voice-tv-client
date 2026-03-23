package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val count: Int,
    val offset: Int,
    val limit: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<SearchSongResult>,
)

@Serializable
data class SearchSongResult(
    val id: Int,
    val name: String,
    val image: String? = null,
    val user: SearchSongUser,
    val length: Double = 0.0,
    @SerialName("plays_count") val playsCount: Int = 0,
)

@Serializable
data class SearchSongUser(
    val username: String,
    val nickname: String? = null,
)
