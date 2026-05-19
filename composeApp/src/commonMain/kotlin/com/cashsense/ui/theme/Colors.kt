package com.cashsense.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val PrimaryPurple = Color(0xFF6C63FF)
val PrimaryPurpleDark = Color(0xFF5A52D6)
val SecondaryCoral = Color(0xFFFF6584)
val BackgroundLight = Color(0xFFF8F9FF)
val SurfaceWhite = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFFF4B4B)
val SuccessGreen = Color(0xFF00D09C)
val NeutralText = Color(0xFF2C3E50)
val CardBorder = Color(0xFFE8ECF4)

val CashSenseLightColors = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = PrimaryPurpleDark,
    secondary = SecondaryCoral,
    onSecondary = Color.White,
    background = BackgroundLight,
    surface = SurfaceWhite,
    onBackground = NeutralText,
    onSurface = NeutralText,
    error = ErrorRed,
    onError = Color.White,
    surfaceVariant = CardBorder,
    onSurfaceVariant = NeutralText,
    tertiary = SuccessGreen,
    onTertiary = Color.White
)

val CashSenseDarkColors = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = PrimaryPurpleDark,
    secondary = SecondaryCoral,
    onSecondary = Color.White,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    error = ErrorRed,
    onError = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0),
    tertiary = SuccessGreen,
    onTertiary = Color.White
)
