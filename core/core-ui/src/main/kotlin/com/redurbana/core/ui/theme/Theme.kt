package com.redurbana.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RedUrbanaDarkScheme = darkColorScheme(
    primary = RedUrbanaColors.AccentGreenPrimary,
    onPrimary = Color.Black,
    secondary = RedUrbanaColors.AccentBlue,
    error = RedUrbanaColors.AlertRed,
    background = RedUrbanaColors.BackgroundPrimary,
    onBackground = RedUrbanaColors.TextPrimary,
    surface = RedUrbanaColors.SurfaceElevated,
    onSurface = RedUrbanaColors.TextPrimary,
    surfaceVariant = RedUrbanaColors.SurfaceCard,
    onSurfaceVariant = RedUrbanaColors.TextSecondary,
    outline = RedUrbanaColors.Divider,
)

// Se ofrece un esquema claro básico para cumplir el requisito de "Tema" en Ajustes,
// pero el default de la app —y el diseñado contra la referencia— es el oscuro.
private val RedUrbanaLightScheme = lightColorScheme(
    primary = RedUrbanaColors.AccentGreenSoft,
    secondary = RedUrbanaColors.AccentBlue,
    error = RedUrbanaColors.AlertRed,
)

@Composable
fun RedUrbanaTheme(
    darkTheme: Boolean = true, // oscuro por defecto, independiente del sistema
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) RedUrbanaDarkScheme else RedUrbanaLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RedUrbanaTypography,
        content = content,
    )
}
