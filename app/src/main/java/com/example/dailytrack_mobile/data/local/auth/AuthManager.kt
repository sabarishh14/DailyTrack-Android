package com.example.dailytrack_mobile.data.local.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        val KEY_AUTH_TOKEN = stringPreferencesKey("auth_jwt_token")
        val KEY_USER_EMAIL = stringPreferencesKey("auth_user_email")
        val KEY_USER_NAME = stringPreferencesKey("auth_user_name")
        val KEY_USER_PHOTO = stringPreferencesKey("auth_user_photo")
        val KEY_IS_ADMIN = booleanPreferencesKey("auth_is_admin")
        val KEY_ACCESS_JSON = stringPreferencesKey("auth_access_json")
        val KEY_VIEW_AS = stringPreferencesKey("auth_view_as")
    }

    /**
     * Whose data is on screen: null for your own, or the email of someone who
     * shared theirs with you (read-only; the server enforces it). Sent as
     * X-View-As on every request.
     */
    val viewAsFlow: StateFlow<String?> = context.authDataStore.data.map { it[KEY_VIEW_AS] }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    @Volatile
    private var inMemoryViewAs: String? = null

    fun getViewAs(): String? = inMemoryViewAs

    suspend fun setViewAs(email: String?) {
        val next = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        inMemoryViewAs = next
        context.authDataStore.edit { preferences ->
            if (next == null) preferences.remove(KEY_VIEW_AS) else preferences[KEY_VIEW_AS] = next
        }
    }

    /** People who shared their data with you, and what (from /api/auth/me). */
    private val _sharedWithMe = MutableStateFlow<List<SharedWithMe>>(emptyList())
    val sharedWithMe: StateFlow<List<SharedWithMe>> = _sharedWithMe.asStateFlow()
    fun saveSharedWithMe(list: List<SharedWithMe>) { _sharedWithMe.value = list }

    /** Admins: people waiting to be let in. */
    private val _pendingRequests = MutableStateFlow(0)
    val pendingRequests: StateFlow<Int> = _pendingRequests.asStateFlow()
    fun savePendingRequests(count: Int) { _pendingRequests.value = count }

    data class SharedWithMe(val owner: String, val modules: List<String>)

    val authTokenFlow: Flow<String?> = context.authDataStore.data.map { preferences ->
        preferences[KEY_AUTH_TOKEN]
    }

    val userEmailFlow: Flow<String?> = context.authDataStore.data.map { preferences ->
        preferences[KEY_USER_EMAIL]
    }

    val userNameFlow: Flow<String?> = context.authDataStore.data.map { preferences ->
        preferences[KEY_USER_NAME]
    }

    val isAdminFlow: Flow<Boolean> = context.authDataStore.data.map { preferences ->
        preferences[KEY_IS_ADMIN] ?: false
    }

    /**
     * Last known permissions. Falls back to the stored admin flag for sessions
     * that predate access control, so the owner never sees a stripped-down UI
     * while /api/auth/me is in flight.
     */
    val accessFlow: StateFlow<AccessInfo?> = context.authDataStore.data.map { preferences ->
        AccessInfo.fromJson(preferences[KEY_ACCESS_JSON])
            ?: if (preferences[KEY_IS_ADMIN] == true) AccessInfo.FULL else null
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    /** Shown on the login screen after a forced sign-out (e.g. access revoked). */
    private val _sessionNotice = MutableStateFlow<String?>(null)
    val sessionNotice: StateFlow<String?> = _sessionNotice.asStateFlow()

    fun consumeSessionNotice() {
        _sessionNotice.value = null
    }

    val isLoggedInFlow: StateFlow<Boolean?> = context.authDataStore.data.map { preferences ->
        val token = preferences[KEY_AUTH_TOKEN]
        !token.isNullOrBlank()
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    @Volatile
    private var inMemoryToken: String? = null

    init {
        // Cache token in memory for synchronous OkHttp interceptor reads
        scope.launch {
            authTokenFlow.collect { token ->
                inMemoryToken = token
            }
        }
        scope.launch { context.authDataStore.data.collect { inMemoryViewAs = it[KEY_VIEW_AS] } }
    }

    /** Reads the stored view before the first request goes out (app start). */
    suspend fun primeViewAs() {
        inMemoryViewAs = context.authDataStore.data.first()[KEY_VIEW_AS]
    }

    fun getCachedToken(): String? = inMemoryToken

    suspend fun saveSession(
        token: String,
        email: String,
        name: String? = null,
        photoUrl: String? = null,
        isAdmin: Boolean = false,
        access: AccessInfo? = null
    ) {
        inMemoryToken = token
        _sessionNotice.value = null
        context.authDataStore.edit { preferences ->
            preferences[KEY_AUTH_TOKEN] = token
            preferences[KEY_USER_EMAIL] = email
            if (name != null) preferences[KEY_USER_NAME] = name
            if (photoUrl != null) preferences[KEY_USER_PHOTO] = photoUrl
            preferences[KEY_IS_ADMIN] = isAdmin
            if (access != null) preferences[KEY_ACCESS_JSON] = access.toJson()
        }
    }

    suspend fun saveAccess(access: AccessInfo) {
        context.authDataStore.edit { preferences ->
            preferences[KEY_ACCESS_JSON] = access.toJson()
            preferences[KEY_IS_ADMIN] = access.isAdmin
        }
    }

    /**
     * Called from the network layer when the server rejects our token. Safe to
     * call from any thread; only the first call for a session does anything.
     */
    fun onSessionRejected(notice: String, sentWith: String?) {
        // Only the session that request belonged to ends: a 401 for a request sent
        // signed out (or with a previous account's token) arriving after a fresh
        // sign-in must not sign the new account out.
        if (inMemoryToken == null || sentWith == null || sentWith != inMemoryToken) return
        inMemoryToken = null
        _sessionNotice.value = notice
        scope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            } catch (_: Exception) { }
            clearSession()
        }
    }

    suspend fun clearSession() {
        inMemoryToken = null
        inMemoryViewAs = null
        _sharedWithMe.value = emptyList()
        _pendingRequests.value = 0
        context.authDataStore.edit { preferences ->
            preferences.remove(KEY_VIEW_AS)
            preferences.remove(KEY_AUTH_TOKEN)
            preferences.remove(KEY_USER_EMAIL)
            preferences.remove(KEY_USER_NAME)
            preferences.remove(KEY_USER_PHOTO)
            preferences.remove(KEY_IS_ADMIN)
            preferences.remove(KEY_ACCESS_JSON)
        }
    }
}
