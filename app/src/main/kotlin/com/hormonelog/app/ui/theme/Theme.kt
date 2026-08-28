package com.hormonelog.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val HlColorScheme = darkColorScheme(
    primary = HlColor.Teal,
    onPrimary = HlColor.OnTeal,
    primaryContainer = HlColor.TealTint,
    onPrimaryContainer = HlColor.Teal,
    secondary = HlColor.Yellow,
    onSecondary = HlColor.OnYellow,
    tertiary = HlColor.Orange,
    background = HlColor.Background,
    onBackground = HlColor.TextPrimary,
    surface = HlColor.Card,
    onSurface = HlColor.TextPrimary,
    surfaceVariant = HlColor.InputSurface,
    onSurfaceVariant = HlColor.TextMuted,
    // Material3 date/time pickers pull their container tints from these.
    surfaceContainerHigh = HlColor.Card,
    surfaceContainerHighest = HlColor.InputSurface,
    outline = HlColor.Border10,
)

/** Single forced-dark theme; the system light/dark setting is ignored by design. */
@Composable
fun HormoneLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HlColorScheme) {
        CompositionLocalProvider(LocalContentColor provides HlColor.TextPrimary, content = content)
    }
}
