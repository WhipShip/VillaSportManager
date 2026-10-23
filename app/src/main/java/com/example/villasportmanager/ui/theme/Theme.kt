package com.example.villasportmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Kayan Club brand scheme. The app is always light and never uses Android 12+ dynamic
 * (wallpaper) colors, so the brand green / lime stay consistent on every device.
 */
private val KayanColorScheme = lightColorScheme(
    primary = KayanGreen,
    onPrimary = Color.White,
    primaryContainer = KayanGreenContainer,
    onPrimaryContainer = OnKayanGreenContainer,
    secondary = KayanGreenDeep,
    onSecondary = Color.White,
    secondaryContainer = KayanLime,
    onSecondaryContainer = OnKayanLime,
    tertiary = KayanLime,
    onTertiary = OnKayanLime,
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SlotBooked,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Mist,
    outline = SlotAvailableBorder,
    outlineVariant = SlotBooked,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun VillaSportManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KayanColorScheme,
        typography = Typography,
        content = content
    )
}
