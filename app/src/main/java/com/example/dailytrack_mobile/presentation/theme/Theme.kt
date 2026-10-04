package com.example.dailytrack_mobile.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.dailytrack_mobile.presentation.util.ScreenSizeTier
import com.example.dailytrack_mobile.presentation.util.screenSizeTierOf

val LocalAppTheme = staticCompositionLocalOf { AppTheme.YELLOW }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailyTrackTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    appTheme: AppTheme = AppTheme.YELLOW,
    withAmoled: Boolean = false,
    appFont: AppFont = AppFont.MODERN,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled by default so theme switcher works on API 31+
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> when (appTheme) {
            AppTheme.YELLOW -> YellowThemeColors.darkScheme
            AppTheme.GREEN -> GreenThemeColors.darkScheme
            AppTheme.TEAL -> TealThemeColors.darkScheme
            AppTheme.PURPLE -> PurpleThemeColors.darkScheme
            AppTheme.JUNE_OLED -> JuneOledThemeColors.darkScheme
            AppTheme.DT_OG -> DtOgThemeColors.darkScheme
            AppTheme.MILES_MORALES -> MilesMoralesThemeColors.darkScheme
            AppTheme.SYNTHWAVE -> SynthwaveThemeColors.darkScheme
            AppTheme.CATPPUCCIN_MOCHA -> CatppuccinMochaThemeColors.darkScheme
        }
        else -> when (appTheme) {
            AppTheme.YELLOW -> YellowThemeColors.lightScheme
            AppTheme.GREEN -> GreenThemeColors.lightScheme
            AppTheme.TEAL -> TealThemeColors.lightScheme
            AppTheme.PURPLE -> PurpleThemeColors.lightScheme
            AppTheme.JUNE_OLED -> JuneOledThemeColors.lightScheme
            AppTheme.DT_OG -> DtOgThemeColors.lightScheme
            AppTheme.MILES_MORALES -> MilesMoralesThemeColors.lightScheme
            AppTheme.SYNTHWAVE -> SynthwaveThemeColors.lightScheme
            AppTheme.CATPPUCCIN_MOCHA -> CatppuccinMochaThemeColors.lightScheme
        }
    }

    val colorScheme = if (darkTheme && (withAmoled || appTheme == AppTheme.JUNE_OLED)) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black
        )
    } else {
        baseScheme
    }

    // Small screens get lighter (narrower) headings; see SyneCompactFamily.
    val compact = screenSizeTierOf(LocalConfiguration.current.screenWidthDp) == ScreenSizeTier.COMPACT
    val typography = remember(appFont, compact) { typographyFor(appFont, compact) }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        // Expressive theme: M3 components pick up the spring-based expressive
        // motion scheme (sheets, nav indicator, buttons, chips, switches...).
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            content = content
        )
    }
}

