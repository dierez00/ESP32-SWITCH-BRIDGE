package com.switchbridge.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BridgeBackground = Color(0xFFEEF4F3)
val BridgeInk = Color(0xFF18323A)
val BridgePanel = Color(0xFFFFFFFF)
val SignalTeal = Color(0xFF00796B)
val HandshakeIndigo = Color(0xFF3F51B5)
val WarningOrange = Color(0xFFC2412D)
val MutedInk = Color(0xFF60787F)
val InactiveRail = Color(0xFFB8C8C9)

private val colors = lightColorScheme(
    primary = SignalTeal,
    onPrimary = Color.White,
    secondary = HandshakeIndigo,
    onSecondary = Color.White,
    error = WarningOrange,
    onError = Color.White,
    background = BridgeBackground,
    onBackground = BridgeInk,
    surface = BridgePanel,
    onSurface = BridgeInk,
    surfaceVariant = Color(0xFFDDE9E7),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFF91A7AA),
)

private val typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        letterSpacing = (-0.6).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun SwitchBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

