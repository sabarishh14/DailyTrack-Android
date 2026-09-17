package com.example.dailytrack_mobile.presentation.screens.settings.components

import com.example.dailytrack_mobile.presentation.screens.settings.ThemePreviewColors
import com.example.dailytrack_mobile.presentation.screens.settings.previewColorsFor
import com.example.dailytrack_mobile.presentation.screens.settings.AutoLockTimeoutDialog
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsAction
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsState
import com.example.dailytrack_mobile.presentation.screens.settings.UpdateStatus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.example.dailytrack_mobile.data.local.security.AppLockManager
import com.example.dailytrack_mobile.data.local.security.LockType
import com.example.dailytrack_mobile.presentation.screens.lock.components.PinVerifyDialog
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.DtOgThemeColors
import com.example.dailytrack_mobile.presentation.theme.GreenThemeColors
import com.example.dailytrack_mobile.presentation.theme.JuneOledThemeColors
import com.example.dailytrack_mobile.presentation.theme.PurpleThemeColors
import com.example.dailytrack_mobile.presentation.theme.TealThemeColors
import com.example.dailytrack_mobile.presentation.theme.ThemeMode
import com.example.dailytrack_mobile.presentation.theme.YellowThemeColors
import com.example.dailytrack_mobile.presentation.util.BiometricHelper
import com.example.dailytrack_mobile.presentation.util.Dimens

// ─────────────────────────────────────────────────────────────────────────────
// Theme Mode Selector – System / Light / Dark segmented controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val dims = Dimens.current
    val modes = listOf(
        Triple(ThemeMode.SYSTEM, "System", Icons.Default.PhoneAndroid),
        Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
        Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(dims.buttonCornerRadius),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (currentMode) {
                        ThemeMode.SYSTEM -> "Follow system settings"
                        ThemeMode.LIGHT -> "Always light"
                        ThemeMode.DARK -> "Always dark"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modes.forEach { (mode, title, icon) ->
                    val isSelected = currentMode == mode
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent,
                        animationSpec = tween(250),
                        label = "modeBg"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(250),
                        label = "modeContent"
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(dims.buttonCornerRadius - 2.dp))
                            .background(bgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onModeSelected(mode) }
                            .padding(vertical = dims.itemSpacingMedium + 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Theme Section – tabs + theme circles (inline inside card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ThemeSectionInCard(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    val moreThemes = remember { MORE_THEMES.map { it.first } }
    var selectedTab by remember {
        mutableIntStateOf(if (selectedTheme in moreThemes) 1 else 0)
    }
    val tabs = listOf("Wallpaper colors", "More themes")
    val dims = Dimens.current

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
    ) {
        // Tab row
        Surface(
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isActive = selectedTab == index
                    val bgColor by animateColorAsState(
                        targetValue = if (isActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent,
                        animationSpec = tween(250),
                        label = "tabBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(250),
                        label = "tabText"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(dims.buttonCornerRadius - 2.dp))
                            .background(bgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedTab = index }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
        }

        // Tab content
        when (selectedTab) {
            0 -> WallpaperColorsContent(selectedTheme, onThemeSelected)
            1 -> MoreThemesContent(selectedTheme, onThemeSelected)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wallpaper colors tab – shows the theme circles with responsive spacing & sizes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun WallpaperColorsContent(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    val dims = Dimens.current
    val wallpaperThemes = listOf(
        AppTheme.YELLOW,
        AppTheme.GREEN,
        AppTheme.TEAL,
        AppTheme.PURPLE,
        AppTheme.JUNE_OLED
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dims.themeCircleSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        wallpaperThemes.forEach { theme ->
            ThemeCircleOption(
                theme = theme,
                isSelected = selectedTheme == theme,
                onClick = { onThemeSelected(theme) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// More themes tab – curated, hand-tuned themes (DT_OG and friends), each
// faithful to its source palette rather than auto-generated from one hue.
// ─────────────────────────────────────────────────────────────────────────────

internal val MORE_THEMES: List<Pair<AppTheme, String>> = listOf(
    AppTheme.DT_OG to "DT OG",
    AppTheme.MILES_MORALES to "Miles",
    AppTheme.SYNTHWAVE to "Synthwave",
    AppTheme.CATPPUCCIN_MOCHA to "Catppuccin",
)

@Composable
internal fun MoreThemesContent(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    val dims = Dimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dims.themeCircleSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MORE_THEMES.forEach { (theme, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeCircleOption(
                    theme = theme,
                    isSelected = selectedTheme == theme,
                    onClick = { onThemeSelected(theme) }
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selectedTheme == theme) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selectedTheme == theme)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual theme circle – split-circle preview + checkmark
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ThemeCircleOption(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    val colors = previewColorsFor(theme)
    val checkColor = MaterialTheme.colorScheme.onPrimary
    val checkBgColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(dims.cardCornerRadius - 2.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.surfaceContainerHighest
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
            .size(dims.themeCircleSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(dims.themeCircleCanvasSize)) {
                drawThemeCircle(colors)
            }
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = checkBgColor,
                    modifier = Modifier
                        .size(dims.themeCircleCheckSize)
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = checkColor,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas helpers – 3-segment circle drawing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws the 3-segment circle that mirrors the Android 12 wallpaper-color picker.
 *
 * Layout:
 * ┌──────────────┐
 * │     top      │
 * │  (primary)   │
 * ├──────┬───────┤
 * │  bL  │  bR   │
 * │(sec) │(tert) │
 * └──────┴───────┘
 */
internal fun DrawScope.drawThemeCircle(colors: ThemePreviewColors) {
    val radius = size.minDimension / 2f
    val center = Offset(radius, radius)
    val gap = 1.5f

    val midX = size.width / 2f
    val midY = size.height / 2f

    drawArcSegment(
        color = colors.top,
        center = center,
        radius = radius,
        clipRect = Rect(0f, 0f, size.width, midY - gap)
    )
    drawArcSegment(
        color = colors.bottomLeft,
        center = center,
        radius = radius,
        clipRect = Rect(0f, midY + gap, midX - gap, size.height)
    )
    drawArcSegment(
        color = colors.bottomRight,
        center = center,
        radius = radius,
        clipRect = Rect(midX + gap, midY + gap, size.width, size.height)
    )
}

internal fun DrawScope.drawArcSegment(
    color: Color,
    center: Offset,
    radius: Float,
    clipRect: Rect
) {
    val path = Path().apply {
        addOval(Rect(center = center, radius = radius))
    }
    drawContext.canvas.save()
    drawContext.canvas.clipRect(
        left = clipRect.left,
        top = clipRect.top,
        right = clipRect.right,
        bottom = clipRect.bottom
    )
    drawPath(path = path, color = color)
    drawContext.canvas.restore()
}
