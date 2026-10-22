package com.example.dailytrack_mobile.presentation.screens.forms

/**
 * A category tapped on the Quick add widget, waiting for the Add screen to
 * open with it. Kept until then, so an app lock or a cold start in between
 * doesn't lose it; taken once, so only that visit starts with it.
 */
object QuickAddRequest {
    @Volatile
    private var category: String? = null

    fun set(value: String?) {
        category = value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun consume(): String? = category.also { category = null }
}
