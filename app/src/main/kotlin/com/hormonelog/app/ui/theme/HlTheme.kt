package com.hormonelog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hormonelog.core.domain.FontScale
import com.hormonelog.core.domain.ThemeMode

/**
 * The 호르몬로그 colour tokens, one set per theme (시안 P0/P1·P2, 색 토큰).
 *
 * Meaning is carried by colour *and* shape *and* a written label — an estimate is a teal
 * band, a measurement a yellow diamond — so nothing depends on colour alone.
 */
@Immutable
class HlColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val input: Color,
    val line: Color,
    val teal: Color,
    val tealSoft: Color,
    val onTeal: Color,
    /** Text colour for a measured value. */
    val yellow: Color,
    /** Fill for the measured-value diamond; brighter than [yellow] in the light theme. */
    val yMark: Color,
    val yellowSoft: Color,
    val orange: Color,
    val orangeSoft: Color,
    val blue: Color,
    val blueSoft: Color,
    val violet: Color,
    val violetSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val disc: Color,
    val scrim: Color,
    val guide: Color,
)

val DarkHlColors = HlColors(
    isDark = true,
    bg = Color(0xFF0F1115), card = Color(0xFF151820), input = Color(0xFF12151B), line = Color(0xFF272C37),
    teal = Color(0xFF7ED6C5), tealSoft = Color(0x217ED6C5), onTeal = Color(0xFF0A2622),
    yellow = Color(0xFFFFD166), yMark = Color(0xFFFFD166), yellowSoft = Color(0x1FFFD166),
    orange = Color(0xFFF0A868), orangeSoft = Color(0x21F0A868),
    blue = Color(0xFF9DB4FF), blueSoft = Color(0x219DB4FF),
    violet = Color(0xFFC9A7F5), violetSoft = Color(0x21C9A7F5),
    danger = Color(0xFFE68A7C), dangerSoft = Color(0x21E68A7C),
    text = Color(0xFFE9ECF3), muted = Color(0xFFB0B6C4), dim = Color(0xFF9AA1B1), disc = Color(0xFFA3AAB9),
    scrim = Color(0x99000000), guide = Color(0x0FE9ECF3),
)

val LightHlColors = HlColors(
    isDark = false,
    bg = Color(0xFFF4F5F7), card = Color(0xFFFFFFFF), input = Color(0xFFEDEFF3), line = Color(0xFFDADEE6),
    teal = Color(0xFF0E7466), tealSoft = Color(0x170E7466), onTeal = Color(0xFFFFFFFF),
    yellow = Color(0xFF7F5600), yMark = Color(0xFFF2B21B), yellowSoft = Color(0x24F2B21B),
    orange = Color(0xFF9C4E0E), orangeSoft = Color(0x179C4E0E),
    blue = Color(0xFF3450C9), blueSoft = Color(0x173450C9),
    violet = Color(0xFF7442B8), violetSoft = Color(0x177442B8),
    danger = Color(0xFFB23A2C), dangerSoft = Color(0x14B23A2C),
    text = Color(0xFF151820), muted = Color(0xFF454C5C), dim = Color(0xFF555C6D), disc = Color(0xFF555C6D),
    scrim = Color(0x73151820), guide = Color(0x0D151820),
)

val LocalHlColors = staticCompositionLocalOf { DarkHlColors }

/** Entry point for the tokens: `Hl.colors.teal`. */
object Hl {
    val colors: HlColors
        @Composable @ReadOnlyComposable get() = LocalHlColors.current
}

/** Whether [mode] resolves to the dark palette right now. */
@Composable
@ReadOnlyComposable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

/**
 * Applies the palette and the user's extra text scale. Sizes are `sp`, so the system font
 * size always counts; [fontScale] multiplies on top of it, and nothing has a fixed height
 * that text could outgrow.
 */
@Composable
fun HormoneLogTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: FontScale = FontScale.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkFor(mode)) DarkHlColors else LightHlColors
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * fontScale.multiplier)
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.teal, onPrimary = colors.onTeal, secondary = colors.yellow, tertiary = colors.orange,
            background = colors.bg, onBackground = colors.text, surface = colors.card, onSurface = colors.text,
            surfaceVariant = colors.input, onSurfaceVariant = colors.muted, outline = colors.line, error = colors.danger,
            surfaceContainerLowest = colors.card, surfaceContainerLow = colors.card, surfaceContainer = colors.card,
            surfaceContainerHigh = colors.card, surfaceContainerHighest = colors.input,
            primaryContainer = colors.tealSoft, onPrimaryContainer = colors.teal,
        )
    } else {
        lightColorScheme(
            primary = colors.teal, onPrimary = colors.onTeal, secondary = colors.yellow, tertiary = colors.orange,
            background = colors.bg, onBackground = colors.text, surface = colors.card, onSurface = colors.text,
            surfaceVariant = colors.input, onSurfaceVariant = colors.muted, outline = colors.line, error = colors.danger,
            surfaceContainerLowest = colors.card, surfaceContainerLow = colors.card, surfaceContainer = colors.card,
            surfaceContainerHigh = colors.card, surfaceContainerHighest = colors.input,
            primaryContainer = colors.tealSoft, onPrimaryContainer = colors.teal,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalHlColors provides colors,
            LocalContentColor provides colors.text,
            LocalDensity provides scaled,
            content = content,
        )
    }
}

/** The type scale (sp): 12 caption · 13 secondary · 14 body · 16 emphasis · 18 sheet title · 22 screen title · 40 hero. */
object HlSize {
    val t11 = 11.sp
    val t12 = 12.sp
    val t13 = 13.sp
    val t14 = 14.sp
    val t16 = 16.sp
    val t18 = 18.sp
    val t22 = 22.sp
    val t40 = 40.sp
}

/** Corner radii (dp): 10 small chip · 12 chip/input · 14 button/tile · 16 card · 20 hero · 24 sheet. */
object HlRadius {
    val chipSmall = 10.dp
    val chip = 12.dp
    val button = 14.dp
    val card = 16.dp
    val hero = 20.dp
    val sheet = 24.dp
}

/**
 * Text with the design's defaults. [lineHeight] is a multiplier of [size], as in the
 * canvas (`line-height:1.5`); leave it null to use the font's own.
 */
@Composable
fun HlText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = HlSize.t14,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Hl.colors.text,
    lineHeight: Float? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    decoration: TextDecoration? = null,
    tabular: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textDecoration = decoration,
        textAlign = align,
        style = style(size, weight, lineHeight, letterSpacing, tabular),
    )
}

@Composable
fun HlText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    size: TextUnit = HlSize.t14,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Hl.colors.text,
    lineHeight: Float? = null,
    align: TextAlign? = null,
) {
    Text(text = text, modifier = modifier, color = color, textAlign = align, style = style(size, weight, lineHeight, TextUnit.Unspecified, false))
}

private fun style(size: TextUnit, weight: FontWeight, lineHeight: Float?, letterSpacing: TextUnit, tabular: Boolean) = TextStyle(
    fontSize = size,
    fontWeight = weight,
    lineHeight = lineHeight?.let { (size.value * it).sp } ?: TextUnit.Unspecified,
    letterSpacing = letterSpacing,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** -0.02em, the hero number's tracking. */
val HeroTracking = (-0.02).em
