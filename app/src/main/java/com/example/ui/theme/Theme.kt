package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CustomThemeColors(
    val heroCardBg: Color,
    val subCardBg: Color,
    val heroAmountColor: Color,
    val actionPrimaryBg: Color,
    val actionSecondaryBg: Color,
    val borderStroke: Color,
    val chipSelectedBg: Color
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomThemeColors(
        heroCardBg = DarkSurface,
        subCardBg = DarkSubCard,
        heroAmountColor = DarkPrimaryLight,
        actionPrimaryBg = DarkPrimary,
        actionSecondaryBg = DarkSurfaceVariant,
        borderStroke = DarkBorder,
        chipSelectedBg = DarkPrimary
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkPrimaryVariant,
    onPrimaryContainer = DarkPrimaryLight,
    secondary = DarkPrimaryLight,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkBorder,
    outlineVariant = DarkBorder.copy(alpha = 0.5f)
)

private val SageColorScheme = darkColorScheme(
    primary = SagePrimary,
    onPrimary = Color.White,
    primaryContainer = SagePrimaryVariant,
    onPrimaryContainer = SagePrimaryLight,
    secondary = SagePrimaryLight,
    onSecondary = SageBackground,
    background = SageBackground,
    onBackground = SageOnBackground,
    surface = SageSurface,
    onSurface = SageOnSurface,
    surfaceVariant = SageSurfaceVariant,
    onSurfaceVariant = SageOnSurfaceVariant,
    outline = SageBorder,
    outlineVariant = SageBorder.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryVariant,
    onPrimaryContainer = LightPrimaryLight,
    secondary = LightPrimary,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.5f)
)

@Composable
fun KasirProTheme(
    themeMode: String = "DARK", // "DARK", "SAGE", "LIGHT"
    content: @Composable () -> Unit
) {
    val (colorScheme, customColors) = when (themeMode) {
        "SAGE" -> Pair(
            SageColorScheme,
            CustomThemeColors(
                heroCardBg = SageSurface,
                subCardBg = SageSubCard,
                heroAmountColor = SagePrimaryLight,
                actionPrimaryBg = SagePrimary,
                actionSecondaryBg = SageSurfaceVariant,
                borderStroke = SageBorder,
                chipSelectedBg = SagePrimary
            )
        )
        "LIGHT" -> Pair(
            LightColorScheme,
            CustomThemeColors(
                heroCardBg = LightSurface,
                subCardBg = LightSubCard,
                heroAmountColor = LightPrimary,
                actionPrimaryBg = LightPrimary,
                actionSecondaryBg = LightSurfaceVariant,
                borderStroke = LightBorder,
                chipSelectedBg = LightPrimary
            )
        )
        else -> Pair(
            DarkColorScheme,
            CustomThemeColors(
                heroCardBg = DarkSurface,
                subCardBg = DarkSubCard,
                heroAmountColor = DarkPrimaryLight,
                actionPrimaryBg = DarkPrimary,
                actionSecondaryBg = DarkSurfaceVariant,
                borderStroke = DarkBorder,
                chipSelectedBg = DarkPrimary
            )
        )
    }

    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
