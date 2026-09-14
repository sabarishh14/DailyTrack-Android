package com.example.dailytrack_mobile.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InvestPreferencesManager(private val context: Context) {

    companion object {
        private const val SYNC_PREFS_NAME = "invest_prefs_sync_cache"
        private const val KEY_HIDDEN_CATS = "hidden_categories"
        val HIDDEN_CATEGORIES_KEY = stringSetPreferencesKey(KEY_HIDDEN_CATS)
    }

    private val syncPrefs by lazy {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun cacheHiddenCategories(names: Set<String>) {
        syncPrefs.edit().putStringSet(KEY_HIDDEN_CATS, names).apply()
    }

    fun getInitialHiddenCategories(): Set<InvestCategory> {
        val stored = syncPrefs.getStringSet(KEY_HIDDEN_CATS, null) ?: return emptySet()
        return stored.mapNotNull { name ->
            try {
                InvestCategory.valueOf(name)
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    val hiddenCategoriesFlow: Flow<Set<InvestCategory>> = context.dataStore.data.map { preferences ->
        val stringSet = preferences[HIDDEN_CATEGORIES_KEY] ?: emptySet()
        cacheHiddenCategories(stringSet)
        stringSet.mapNotNull { name ->
            try {
                InvestCategory.valueOf(name)
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    suspend fun setHiddenCategories(hiddenCategories: Set<InvestCategory>) {
        val stringSet = hiddenCategories.map { it.name }.toSet()
        cacheHiddenCategories(stringSet)
        context.dataStore.edit { preferences ->
            preferences[HIDDEN_CATEGORIES_KEY] = stringSet
        }
    }

    suspend fun toggleCategory(category: InvestCategory) {
        val current = getInitialHiddenCategories()
        val updated = if (category in current) {
            current - category
        } else {
            current + category
        }
        setHiddenCategories(updated)
    }

    suspend fun setCategoryHidden(category: InvestCategory, isHidden: Boolean) {
        val current = getInitialHiddenCategories()
        val updated = if (isHidden) {
            current + category
        } else {
            current - category
        }
        setHiddenCategories(updated)
    }

    suspend fun setAllCategoriesVisibility(showAll: Boolean) {
        val updated = if (showAll) {
            emptySet()
        } else {
            InvestCategory.entries.toSet()
        }
        setHiddenCategories(updated)
    }
}
