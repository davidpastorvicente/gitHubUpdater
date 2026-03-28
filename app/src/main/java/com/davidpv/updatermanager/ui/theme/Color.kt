package com.davidpv.updatermanager.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val SkyPrimary = Color(0xFF4F6BFF)
val SkyPrimaryContainer = Color(0xFFDDE1FF)
val NightPrimary = Color(0xFFBBC3FF)
val NightPrimaryContainer = Color(0xFF3247D3)
val MintSecondary = Color(0xFF2D6A5E)
val MintSecondaryContainer = Color(0xFFAEEBDC)
val NightSecondary = Color(0xFF90D5C5)
val NightSecondaryContainer = Color(0xFF115045)
val CoralTertiary = Color(0xFF8F4B39)
val CoralTertiaryContainer = Color(0xFFFFDBD2)
val NightTertiary = Color(0xFFFFB4A0)
val NightTertiaryContainer = Color(0xFF733423)

data class StatusPalette(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

val LocalStatusPalette = staticCompositionLocalOf {
    StatusPalette(
        success = Color(0xFF006D3B),
        onSuccess = Color.White,
        successContainer = Color(0xFF91F7B3),
        onSuccessContainer = Color(0xFF00210F),
        warning = Color(0xFF805500),
        onWarning = Color.White,
        warningContainer = Color(0xFFFFDDB0),
        onWarningContainer = Color(0xFF281800),
        info = Color(0xFF0057D0),
        onInfo = Color.White,
        infoContainer = Color(0xFFD8E2FF),
        onInfoContainer = Color(0xFF001946),
    )
}
