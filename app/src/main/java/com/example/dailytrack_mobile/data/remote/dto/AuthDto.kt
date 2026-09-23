package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FirebaseLoginRequestDto(
    @param:Json(name = "id_token") val id_token: String
)

@JsonClass(generateAdapter = true)
data class FirebaseLoginResponseDto(
    @param:Json(name = "success") val success: Boolean,
    @param:Json(name = "token") val token: String? = null,
    @param:Json(name = "isAdmin") val isAdmin: Boolean? = null,
    @param:Json(name = "access") val access: AccessDto? = null,
    @param:Json(name = "message") val message: String? = null
)
