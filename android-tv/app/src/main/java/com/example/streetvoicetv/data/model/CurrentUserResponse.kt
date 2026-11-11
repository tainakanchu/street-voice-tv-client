package com.example.streetvoicetv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/api/v4/user/me/`: the logged-in user. Only [username] is required. */
@Serializable
data class CurrentUserResponse(
    val username: String,
    val id: Int? = null,
    val nickname: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
)
