package com.example.dailytrack_mobile.data.repository

import android.os.SystemClock
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.local.datastore.SyncPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opening the app pulls in what's new on Letterboxd: a quick look at its latest
 * few entries, in the background, at most every 15 minutes. The full import
 * stays in Settings → Sync. The web does the same on load (App.jsx).
 */
@Singleton
class LetterboxdAutoSync @Inject constructor(
    private val sabdekho: SabdekhoRepository,
    private val syncPreferences: SyncPreferencesManager,
    private val authManager: AuthManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var lastRun = 0L

    fun onAppOpened() {
        if (lastRun != 0L && SystemClock.elapsedRealtime() - lastRun < EVERY_MS) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                // On a cold start the sign-in is still being read from storage.
                if (authManager.isLoggedInFlow.filterNotNull().first() && authManager.viewAsFlow.value == null &&
                    authManager.accessFlow.value?.canEdit(AccessModule.SABDEKHO) == true
                ) {
                    val username = syncPreferences.getLetterboxdUsername()
                    if (username.isNotBlank()) {
                        lastRun = SystemClock.elapsedRealtime()
                        sabdekho.syncLetterboxd(username, fast = true)   // quiet: a miss waits for the next open
                    }
                }
            } finally {
                running.set(false)
            }
        }
    }

    private companion object {
        const val EVERY_MS = 15 * 60 * 1000L
    }
}
