package com.redurbana.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Asigna color a una línea de forma determinística a partir de su `colorSeed`
 * (ver RouteDetails.colorSeed en domain-transport), en vez de hardcodear
 * "línea 60 = rojo" en la UI. Si en el futuro una fuente de datos real trae
 * un color oficial por línea, se puede priorizar ese valor aquí sin tocar
 * ninguna pantalla que consuma este objeto.
 */
object LineColorProvider {

    fun colorFor(colorSeed: String, officialColorHex: String? = null): Color {
        officialColorHex?.let { hex ->
            runCatching { return Color(android.graphics.Color.parseColor(hex)) }
        }
        val index = abs(colorSeed.hashCode()) % RedUrbanaColors.linePalette.size
        return RedUrbanaColors.linePalette[index]
    }
}
