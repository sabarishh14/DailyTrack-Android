package com.example.dailytrack_mobile.data.vault

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings › Kite holdings (Manual or Daily), which the server keeps for the
 * signed-in person: the vault's engine reprices on Daily, as the server did.
 * Asked for at most once a minute, and again after it's changed.
 */
@Singleton
class KiteModeSource @Inject constructor(private val store: VaultStore) {
    @Volatile private var cached: Pair<Long, String>? = null

    fun mode(): String {
        cached?.let { if (System.currentTimeMillis() - it.first < 60_000) return it.second }
        return runCatching {
            val me = store.call("GET", "/api/auth/me")
            (if (me.optJSONObject("settings")?.optString("kite_valuation") == "daily") "daily" else "manual")
                .also { cached = System.currentTimeMillis() to it }
        }.getOrElse { cached?.second ?: "manual" }
    }

    fun forget() { cached = null }
}
