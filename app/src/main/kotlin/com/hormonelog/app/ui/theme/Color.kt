package com.hormonelog.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette lifted verbatim from the 호르몬로그 design canvas (호르몬로그.dc.html).
 * Dark only — the app ships a single forced-dark theme.
 */
object HlColor {
    // Surfaces
    val Background = Color(0xFF0F1115)
    val NavBackground = Color(0xFF12141A)
    val Card = Color(0xFF151820)
    val CardAlt = Color(0xFF131620)
    val InputSurface = Color(0xFF12151B)
    val ChipIdle = Color(0xFF171A21)
    val ButtonIdle = Color(0xFF1B1F27)
    val RowIdle = Color(0xFF181B22)
    val KeyIdle = Color(0xFF1E222B)
    val KeyAlt = Color(0xFF222630)
    val HeroGradientTop = Color(0xFF182028)
    val HeroGradientBottom = Color(0xFF151820)

    // Accents
    val Teal = Color(0xFF7ED6C5)
    val OnTeal = Color(0xFF08201C)
    val Yellow = Color(0xFFFFD166)
    val OnYellow = Color(0xFF241D07)
    val Orange = Color(0xFFF0A868)
    val Blue = Color(0xFF9DB4FF)
    val Danger = Color(0xFFE68A7C)

    // Text
    val TextPrimary = Color(0xFFE9ECF3)
    val TextBright = Color(0xFFDFE4EE)
    val TextSecondary = Color(0xFFB9C0CF)
    val TextMuted = Color(0xFF8F97A8)
    val TextDim = Color(0xFF6F7688)
    val TextFaint = Color(0xFF5C6274)
    val TextDisclaimer = Color(0xFF666D7E)
    val TextPlaceholder = Color(0xFF4A5162)

    // Toast
    val ToastBackground = Color(0xFFE9ECF3)
    val ToastForeground = Color(0xFF10131A)

    // Hairlines
    val Border06 = Color(0x0FFFFFFF)
    val Border08 = Color(0x14FFFFFF)
    val Border10 = Color(0x1AFFFFFF)

    // Accent tints
    val TealTint = Color(0x1F7ED6C5)
    val TealTintSoft = Color(0x1A7ED6C5)
    val YellowTint = Color(0x21FFD166)
    val YellowTintSoft = Color(0x17FFD166)
    val OrangeTint = Color(0x24F0A868)
    val OrangeTintSoft = Color(0x1FF0A868)
    val DangerTintSoft = Color(0x1FE68A7C)
    val NeutralTint = Color(0x0FFFFFFF)
}
