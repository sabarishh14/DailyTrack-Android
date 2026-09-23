package com.example.dailytrack_mobile.presentation.access

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.dailytrack_mobile.data.local.auth.AccessInfo

/**
 * The signed-in user's permissions, provided once in MainActivity so any screen
 * can hide what the user can't use: `LocalAccess.current.canEdit(AccessModule.MONEY)`.
 */
val LocalAccess = staticCompositionLocalOf { AccessInfo.NONE }
