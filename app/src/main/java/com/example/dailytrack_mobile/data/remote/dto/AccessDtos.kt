package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Shapes documented in DT-Web/ACCESS_CONTROL.md ("API reference").

@JsonClass(generateAdapter = true)
data class MoneyAccessDto(
    @param:Json(name = "categories") val categories: List<String>? = null,
    @param:Json(name = "accounts") val accounts: List<String>? = null,
    @param:Json(name = "restricted") val restricted: Boolean = false,
    @param:Json(name = "balancesVisible") val balancesVisible: Boolean = false,
    @param:Json(name = "fullAccess") val fullAccess: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AccessDto(
    @param:Json(name = "email") val email: String? = null,
    @param:Json(name = "role") val role: String = "member",
    @param:Json(name = "isOwner") val isOwner: Boolean = false,
    @param:Json(name = "isAdmin") val isAdmin: Boolean = false,
    @param:Json(name = "modules") val modules: Map<String, String> = emptyMap(),
    @param:Json(name = "money") val money: MoneyAccessDto = MoneyAccessDto()
)

@JsonClass(generateAdapter = true)
data class MeResponseDto(
    @param:Json(name = "success") val success: Boolean,
    @param:Json(name = "access") val access: AccessDto? = null
)

// ---- Admin: people & permissions ----

@JsonClass(generateAdapter = true)
data class MoneyScopeDto(
    @param:Json(name = "categories") val categories: List<String>? = null,
    @param:Json(name = "accounts") val accounts: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class PermissionsDto(
    @param:Json(name = "modules") val modules: Map<String, String> = emptyMap(),
    @param:Json(name = "money_scope") val moneyScope: MoneyScopeDto = MoneyScopeDto()
)

@JsonClass(generateAdapter = true)
data class AccessUserDto(
    @param:Json(name = "email") val email: String,
    @param:Json(name = "role") val role: String = "member",
    @param:Json(name = "legacy") val legacy: Boolean = false,
    @param:Json(name = "permissions") val permissions: PermissionsDto = PermissionsDto(),
    @param:Json(name = "added_on") val addedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class AccessUsersResponseDto(
    @param:Json(name = "success") val success: Boolean,
    @param:Json(name = "owners") val owners: List<String> = emptyList(),
    @param:Json(name = "users") val users: List<AccessUserDto> = emptyList(),
    @param:Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AccessOptionsResponseDto(
    @param:Json(name = "success") val success: Boolean,
    @param:Json(name = "categories") val categories: List<String> = emptyList(),
    @param:Json(name = "accounts") val accounts: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AccessUserRequestDto(
    @param:Json(name = "email") val email: String,
    @param:Json(name = "role") val role: String,
    @param:Json(name = "permissions") val permissions: PermissionsDto
)

@JsonClass(generateAdapter = true)
data class AccessUserResponseDto(
    @param:Json(name = "success") val success: Boolean,
    @param:Json(name = "message") val message: String? = null
)
