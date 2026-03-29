package com.babybloom.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary          = NavyDark,
    onPrimary        = White,
    background       = ScreenBackgroundLight,
    onBackground     = TextPrimary,
    surface          = TextFieldBackground,
    onSurface        = TextPrimary,
    error            = ErrorRed,
    onError          = White,
)

@Composable
fun BabyBloomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography  = Typography,
        content     = content
    )
}