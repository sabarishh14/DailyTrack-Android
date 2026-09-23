package com.example.dailytrack_mobile.data.repository

import com.example.dailytrack_mobile.data.local.auth.AccessInfo
import com.example.dailytrack_mobile.data.local.auth.AccessLevel
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.example.dailytrack_mobile.data.remote.dto.AccessDto
import com.example.dailytrack_mobile.data.remote.dto.AccessOptionsResponseDto
import com.example.dailytrack_mobile.data.remote.dto.AccessUserRequestDto
import com.example.dailytrack_mobile.data.remote.dto.AccessUsersResponseDto
import com.example.dailytrack_mobile.data.remote.dto.FirebaseLoginRequestDto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: DailyTrackApi,
    private val authManager: AuthManager
) {
    val isLoggedInFlow: StateFlow<Boolean?> = authManager.isLoggedInFlow
    val userEmailFlow: Flow<String?> = authManager.userEmailFlow
    val userNameFlow: Flow<String?> = authManager.userNameFlow
    val isAdminFlow: Flow<Boolean> = authManager.isAdminFlow
    val accessFlow: StateFlow<AccessInfo?> = authManager.accessFlow
    val sessionNotice: StateFlow<String?> = authManager.sessionNotice

    fun consumeSessionNotice() = authManager.consumeSessionNotice()

    /** Re-reads permissions so role changes apply without signing out. */
    suspend fun refreshAccess(): Result<AccessInfo> {
        if (authManager.getCachedToken().isNullOrBlank()) return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = api.getMyAccess()
            val dto = response.body()?.access
            if (response.isSuccessful && dto != null) {
                val access = dto.toAccessInfo()
                authManager.saveAccess(access)
                Result.success(access)
            } else {
                // 401s are handled centrally by the network layer (signs out).
                Result.failure(Exception("Could not refresh access (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Admin: people & permissions ----
    suspend fun getAccessUsers(): Result<AccessUsersResponseDto> = call { api.getAccessUsers() }
    suspend fun getAccessOptions(): Result<AccessOptionsResponseDto> = call { api.getAccessOptions() }

    suspend fun saveAccessUser(request: AccessUserRequestDto, isNew: Boolean): Result<Unit> =
        call { if (isNew) api.createAccessUser(request) else api.updateAccessUser(request.email, request) }
            .mapCatching { if (!it.success) throw Exception(it.message ?: "Could not save") }

    suspend fun deleteAccessUser(email: String): Result<Unit> =
        call { api.deleteAccessUser(email) }
            .mapCatching { if (!it.success) throw Exception(it.message ?: "Could not remove") }

    private suspend fun <T> call(block: suspend () -> retrofit2.Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val serverMessage = response.errorBody()?.string()?.let {
                runCatching { org.json.JSONObject(it).optString("message") }.getOrNull()?.takeIf { m -> m.isNotBlank() }
            }
            Result.failure(Exception(serverMessage ?: "Server error (${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun loginWithFirebaseToken(
        idToken: String,
        email: String,
        name: String? = null,
        photoUrl: String? = null
    ): Result<Boolean> {
        return try {
            val response = api.firebaseLogin(FirebaseLoginRequestDto(id_token = idToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && !body.token.isNullOrBlank()) {
                    authManager.saveSession(
                        token = body.token,
                        email = email,
                        name = name,
                        photoUrl = photoUrl,
                        isAdmin = body.isAdmin ?: false,
                        access = body.access?.toAccessInfo()
                    )
                    Result.success(true)
                } else {
                    Result.failure(Exception(body?.message ?: "Login failed. You may not be an authorized user."))
                }
            } else {
                val errorMsg = when (response.code()) {
                    403 -> "Access denied. Your Google account ($email) is not authorized."
                    401 -> "Invalid credentials or expired Google session."
                    else -> "Server error (${response.code()}). Please try again."
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) { }
        authManager.clearSession()
    }
}

fun AccessDto.toAccessInfo(): AccessInfo = AccessInfo(
    email = email.orEmpty(),
    role = role,
    isOwner = isOwner,
    isAdmin = isAdmin,
    modules = AccessModule.entries.associateWith { AccessLevel.from(modules[it.key]) },
    categories = money.categories,
    accounts = money.accounts,
    moneyRestricted = money.restricted,
    balancesVisible = money.balancesVisible,
    fullMoneyAccess = money.fullAccess
)
