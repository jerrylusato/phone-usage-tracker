package com.jeremiah.phoneusage.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Color(0xFF186A5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2DC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635C),
    tertiary = Color(0xFF3F6375),
    surfaceTint = Color(0xFF186A5B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8BD5C0),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFA7F2DC),
    secondary = Color(0xFFB2CCC3),
    tertiary = Color(0xFFA7CBE0),
    surfaceTint = Color(0xFF8BD5C0),
)

@Composable
fun PhoneUsageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        // Material You: matches the One UI wallpaper palette on Android 12+.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
