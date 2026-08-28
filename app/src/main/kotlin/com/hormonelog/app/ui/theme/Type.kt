package com.hormonelog.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Text styles matching the design canvas. The canvas uses "Pretendard Variable";
 * bundle the .ttf under res/font and swap [Family] here to match exactly.
 */
object HlType {
    val Family: FontFamily = FontFamily.Default

    private fun style(
        size: Int,
        weight: FontWeight,
        lineHeightRatio: Double = 1.3,
        letterSpacingEm: Double = 0.0,
    ) = TextStyle(
        fontFamily = Family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeightRatio).sp,
        letterSpacing = letterSpacingEm.em,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    val ScreenTitle = style(21, FontWeight.Bold, 1.2, -0.02)
    val HeroNumber = style(42, FontWeight.Bold, 1.0, -0.03)
    val HeroUnit = style(14, FontWeight.Medium, 1.6)
    val SectionHeader = style(13, FontWeight.SemiBold, 1.0)
    val CardTitleLg = style(15, FontWeight.Bold, 1.2)
    val CardTitle = style(13, FontWeight.SemiBold, 1.25)
    val Body = style(13, FontWeight.Normal, 1.5)
    val BodySm = style(12, FontWeight.Normal, 1.4)
    val Label = style(12, FontWeight.Medium, 1.0)
    val LabelStrong = style(12, FontWeight.SemiBold, 1.0)
    val Caption = style(11, FontWeight.Normal, 1.4)
    val Chip = style(12, FontWeight.SemiBold, 1.0)
    val TabLabel = style(10, FontWeight.SemiBold, 1.0)
    val ButtonLabel = style(16, FontWeight.Bold, 1.0)
    val StatLabel = style(11, FontWeight.Medium, 1.0)
    val StatValue = style(14, FontWeight.SemiBold, 1.2)
    val Badge = style(11, FontWeight.SemiBold, 1.0)
    val Stepper = style(24, FontWeight.Bold, 1.0)
    val Disclaimer = style(11, FontWeight.Normal, 1.5)
}
