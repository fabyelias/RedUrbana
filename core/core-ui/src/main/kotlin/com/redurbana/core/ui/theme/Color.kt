package com.redurbana.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta extraída directamente de la referencia visual del Dashboard de RedUrbana.
 * Modo oscuro por defecto, glassmorphism, acentos verde/azul/rojo con función semántica fija.
 */
object RedUrbanaColors {

    // Fondos
    val BackgroundPrimary = Color(0xFF0A0E12)
    val SurfaceElevated = Color(0xFF141A20)
    val SurfaceGlassLight = Color(0x14FFFFFF) // blanco ~8% para overlays sobre el mapa
    val SurfaceCard = Color(0xFF10161B)
    val Divider = Color(0xFF232B33)

    // Texto
    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFF8B95A1)
    val TextTertiary = Color(0xFF5C6672)

    // Acentos semánticos — NO reasignar su significado en ninguna pantalla
    val AccentGreenPrimary = Color(0xFF22C55E) // CTA principal, "En horario", confiabilidad alta
    val AccentGreenSoft = Color(0xFF1B7A43)
    val AccentBlue = Color(0xFF3B82F6)         // elementos de mapa, recorridos, info neutra
    val AlertRed = Color(0xFFEF4444)           // ÚNICAMENTE alertas/errores/fuera de servicio
    val WarningAmber = Color(0xFFF5A524)       // demoras (no crítico)

    // Colores de línea (dinámicos según la referencia: 60 rojo, 152 azul, 59 verde, 37 amarillo)
    val LineRed = Color(0xFFE53E3E)
    val LineBlue = Color(0xFF3B82F6)
    val LineGreen = Color(0xFF22C55E)
    val LineYellow = Color(0xFFF5C518)
    val LineViolet = Color(0xFF8B5CF6)
    val LinePink = Color(0xFFEC4899)

    /** Paleta rotativa determinística usada por LineColorProvider cuando no hay color fijo. */
    val linePalette = listOf(LineRed, LineBlue, LineGreen, LineYellow, LineViolet, LinePink)
}
