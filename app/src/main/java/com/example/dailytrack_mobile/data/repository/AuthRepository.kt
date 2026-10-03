package com.example.dailytrack_mobile.data.repository

import com.example.dailytrack_mobile.data.local.auth.AccessInfo
import com.example.dailytrack_mobile.data.local.auth.AccessLevel
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.local.auth.PersonalDataReset
import com.example.dailytrack_mobile.data.local.datastore.SyncPreferencesManager
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
    private val authManager: AuthManager,
    private val personalData: PersonalDataReset,
    private val moneyRepository: MoneyRepository,
    private val syncPreferences: SyncPreferencesManager,
    private val demoDataManager: com.example.dailytrack_mobile.data.local.demo.DemoDataManager
) {
    val isLoggedInFlow: StateFlow<Boolean?> = authManager.isLoggedInFlow
    val userEmailFlow: Flow<String?> = authManager.userEmailFlow
    val userNameFlow: Flow<String?> = authManager.userNameFlow
    val isAdminFlow: Flow<Boolean> = authManager.isAdminFlow
    val accessFlow: StateFlow<AccessInfo?> = authManager.accessFlow
    val sessionNotice: StateFlow<String?> = authManager.sessionNotice
    /** Whose data is on screen: null = yours, else someone's shared with you (read-only). */
    val viewAsFlow: StateFlow<String?> = authManager.viewAsFlow
    val sharedWithMe: StateFlow<List<AuthManager.SharedWithMe>> = authManager.sharedWithMe
    val pendingRequests: StateFlow<Int> = authManager.pendingRequests

    fun consumeSessionNotice() = authManager.consumeSessionNotice()

    /** Re-reads permissions so role changes apply without signing out. */
    suspend fun refreshAccess(): Result<AccessInfo> {
        if (authManager.getCachedToken().isNullOrBlank()) return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = api.getMyAccess()
            val dto = response.body()?.access
            if (response.code() == 403 && authManager.getViewAs() != null) {
                // No longer shared with you: back to your own data.
                switchView(null)
                return Result.failure(Exception("No longer shared with you"))
            }
            if (response.isSuccessful && dto != null) {
                val access = dto.toAccessInfo()
                // Sessions from before people were kept apart never claimed this phone.
                personalData.claimFor(access.email)
                authManager.saveAccess(access)
                response.body()?.let { body ->
                    authManager.saveSharedWithMe(body.sharedWithMe.map { AuthManager.SharedWithMe(it.owner, it.modules) })
                    authManager.savePendingRequests(body.pendingRequests)
                    if (access.viewing == null) body.settings?.let { mirrorSettings(it.letterboxdUsername) }
                }
                Result.success(access)
            } else {
                // 401s are handled centrally by the network layer (signs out).
                Result.failure(Exception("Could not refresh access (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The Letterboxd username lives on the server now, so it follows the person.
     * One typed on this phone before that is handed up once instead of lost.
     */
    private suspend fun mirrorSettings(serverLetterboxd: String?) {
        val onServer = serverLetterboxd.orEmpty().trim()
        val onPhone = syncPreferences.getLetterboxdUsername()
        when {
            onServer.isNotEmpty() -> if (onServer != onPhone) syncPreferences.setLetterboxdUsername(onServer)
            onPhone.isNotEmpty() -> runCatching { api.updateMySettings(mapOf("letterboxd_username" to onPhone)) }
        }
    }

    /**
     * Shows someone's shared data (read-only), or your own with null. Cached data
     * is dropped so nothing of one shows as the other's, and every screen reloads.
     */
    suspend fun switchView(owner: String?) {
        authManager.setViewAs(owner)
        personalData.forgetCachedData()
        runCatching {
            val response = api.getMyAccess()
            response.body()?.access?.takeIf { response.isSuccessful }?.let { authManager.saveAccess(it.toAccessInfo()) }
        }
        demoDataManager.notifyDataUpdated()
    }

    // ---- Sharing ----
    suspend fun getShares(): Result<com.example.dailytrack_mobile.data.remote.dto.SharesResponseDto> = call { api.getShares() }

    /** Shares those modules with [viewer], view-only; an empty list stops sharing with them. */
    suspend fun setShare(viewer: String, modules: List<String>): Result<Unit> =
        call { api.setShare(viewer.trim().lowercase(), com.example.dailytrack_mobile.data.remote.dto.ShareRequestDto(modules)) }
            .mapCatching { if (!it.success) throw Exception(it.message ?: "Couldn't save") }

    // ---- Admin: requests to join ----
    suspend fun approveRequest(email: String): Result<Unit> =
        call { api.approveAccessRequest(email) }.mapCatching { if (!it.success) throw Exception(it.message ?: "Couldn't approve") }

    suspend fun declineRequest(email: String): Result<Unit> =
        call { api.declineAccessRequest(email) }.mapCatching { if (!it.success) throw Exception(it.message ?: "Couldn't decline") }

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
                    // Someone else signing in on this phone starts clean.
                    personalData.claimFor(email)
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
                // Not in yet: the server sent the owner a request to approve.
                val requested = response.code() == 403 &&
                    (response.errorBody()?.string()?.contains("REQUESTED") == true)
                val errorMsg = when {
                    requested -> "Request sent. You can sign in once it's approved."
                    response.code() == 403 -> "Access denied. Your Google account ($email) is not authorized."
                    response.code() == 401 -> "Invalid credentials or expired Google session."
                    else -> "Server error (${response.code()}). Please try again."
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        // While still signed in: this phone stops getting their low-balance alerts.
        moneyRepository.unregisterPushToken()
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
    // The phone's routines (alarms, check-ins, widget) are always your own, so
    // Routines hides while someone else's data is on screen.
    modules = AccessModule.entries.associateWith {
        if (it == AccessModule.GYM && !viewing.isNullOrBlank()) AccessLevel.NONE else AccessLevel.from(modules[it.key])
    },
    categories = money.categories,
    accounts = money.accounts,
    moneyRestricted = money.restricted,
    balancesVisible = money.balancesVisible,
    fullMoneyAccess = money.fullAccess,
    viewing = viewing?.takeIf { it.isNotBlank() }
)
