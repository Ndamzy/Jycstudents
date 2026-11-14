package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JyDarkColorScheme =
  darkColorScheme(
    primary = JyDarkPrimary,
    onPrimary = JyDarkOnPrimary,
    primaryContainer = JyDarkPrimaryContainer,
    onPrimaryContainer = JySkyBlue,
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = JyTealContainer,
    tertiary = JyGoldBright,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = JyGoldContainer,
    background = JyDarkBg,
    onBackground = Color(0xFFF1F5F9),
    surface = JyDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = JyDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = JyDarkOutline,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = JyCrimsonContainer,
  )

private val JyLightColorScheme =
  lightColorScheme(
    primary = JyRoyalBlue,
    onPrimary = JyPureWhite,
    primaryContainer = JySkyBlue,
    onPrimaryContainer = JyDeepNavy,
    secondary = JyTeal,
    onSecondary = JyPureWhite,
    secondaryContainer = JyTealContainer,
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = JyGold,
    onTertiary = JyPureWhite,
    tertiaryContainer = JyGoldContainer,
    onTertiaryContainer = Color(0xFF451A03),
    background = JySoftSlate,
    onBackground = JyInkDark,
    surface = JyPureWhite,
    onSurface = JyInkDark,
    surfaceVariant = JyIceBlue,
    onSurfaceVariant = JyMutedSlate,
    outline = JyOutlineLight,
    error = JyCrimson,
    onError = JyPureWhite,
    errorContainer = JyCrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D),
  )

val JyShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun JyComputersTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) JyDarkColorScheme else JyLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = JyShapes,
    content = content,
  )
}
