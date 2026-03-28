package com.davidpv.updatermanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = SkyPrimary,
    onPrimary = Color.White,
    primaryContainer = SkyPrimaryContainer,
    onPrimaryContainer = Color(0xFF001A73),
    secondary = MintSecondary,
    onSecondary = Color.White,
    secondaryContainer = MintSecondaryContainer,
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = CoralTertiary,
    onTertiary = Color.White,
    tertiaryContainer = CoralTertiaryContainer,
    onTertiaryContainer = Color(0xFF380D02),
)

private val DarkColors = darkColorScheme(
    primary = NightPrimary,
    onPrimary = Color(0xFF07218C),
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = NightSecondary,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = NightSecondaryContainer,
    onSecondaryContainer = Color(0xFFAEEBDC),
    tertiary = NightTertiary,
    onTertiary = Color(0xFF561F10),
    tertiaryContainer = NightTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFDBD2),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun UpdaterManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val statusPalette = if (darkTheme) {
        StatusPalette(
            success = Color(0xFF75DB98),
            onSuccess = Color(0xFF00391B),
            successContainer = Color(0xFF005228),
            onSuccessContainer = Color(0xFF91F7B3),
            warning = Color(0xFFF6BE61),
            onWarning = Color(0xFF422C00),
            warningContainer = Color(0xFF5F4100),
            onWarningContainer = Color(0xFFFFDDB0),
            info = Color(0xFFAEC6FF),
            onInfo = Color(0xFF002E69),
            infoContainer = Color(0xFF004494),
            onInfoContainer = Color(0xFFD8E2FF),
        )
    } else {
        LocalStatusPalette.current
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalStatusPalette provides statusPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
