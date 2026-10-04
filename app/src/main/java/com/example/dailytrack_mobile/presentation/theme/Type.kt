package com.example.dailytrack_mobile.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.R

/** The typefaces the app is set in — Settings → Appearance → Font. */
enum class AppFont {
    /** As on the web: Syne for headings and big numbers, DM Sans for everything else. */
    MODERN,

    /** The phone's own font, as the app has always looked. */
    CLASSIC
}

/**
 * Headings and hero numbers. Stops at Bold on purpose: Syne widens sharply
 * past it (ExtraBold sets 1.8× wider than Roboto), so heavier asks land on Bold.
 */
val SyneFamily = FontFamily(
    Font(R.font.syne_medium, FontWeight.Medium),
    Font(R.font.syne_semibold, FontWeight.SemiBold),
    Font(R.font.syne_bold, FontWeight.Bold)
)

/**
 * Syne on screens under 360dp: each weight a step lighter, because in Syne a
 * heavier weight is also a wider one. Headings keep their size and still fit.
 */
private val SyneCompactFamily = FontFamily(
    Font(R.font.syne_medium, FontWeight.Medium),
    Font(R.font.syne_medium, FontWeight.SemiBold),
    Font(R.font.syne_semibold, FontWeight.Bold)
)

/** Body text, labels, list rows and inputs. */
val DmSansFamily = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold)
)

private val ClassicTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Material's sizes and line heights stay as they are, so no layout changes
 * footprint. DM Sans runs a little wider than Roboto with a slightly smaller
 * x-height, so its tracking is pulled in from Roboto's to keep lines the same
 * length. Headings get Syne, as on the web.
 */
private fun modernTypography(compact: Boolean): Typography {
    val heading = if (compact) SyneCompactFamily else SyneFamily
    val base = Typography()

    fun TextStyle.syne(tracking: TextUnit = letterSpacing) =
        copy(fontFamily = heading, fontWeight = FontWeight.SemiBold, letterSpacing = tracking)

    fun TextStyle.dmSans(tracking: TextUnit) =
        copy(fontFamily = DmSansFamily, letterSpacing = tracking)

    return Typography(
        displayLarge = base.displayLarge.syne((-0.5).sp),
        displayMedium = base.displayMedium.syne((-0.25).sp),
        displaySmall = base.displaySmall.syne((-0.25).sp),
        headlineLarge = base.headlineLarge.syne(),
        headlineMedium = base.headlineMedium.syne(),
        headlineSmall = base.headlineSmall.syne(),
        titleLarge = base.titleLarge.syne(),
        titleMedium = base.titleMedium.dmSans(0.1.sp),
        titleSmall = base.titleSmall.dmSans(0.1.sp),
        bodyLarge = base.bodyLarge.dmSans(0.15.sp),
        bodyMedium = base.bodyMedium.dmSans(0.1.sp),
        bodySmall = base.bodySmall.dmSans(0.2.sp),
        labelLarge = base.labelLarge.dmSans(0.1.sp),
        labelMedium = base.labelMedium.dmSans(0.2.sp),
        labelSmall = base.labelSmall.dmSans(0.3.sp)
    )
}

/** [compact]: the screen is under 360dp wide. */
fun typographyFor(font: AppFont, compact: Boolean): Typography = when (font) {
    AppFont.MODERN -> modernTypography(compact)
    AppFont.CLASSIC -> ClassicTypography
}
