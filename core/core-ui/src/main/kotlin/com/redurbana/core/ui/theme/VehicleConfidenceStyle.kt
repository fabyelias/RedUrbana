package com.redurbana.core.ui.theme

/**
 * Cómo se ve un colectivo según cuánta evidencia hay detrás de su posición.
 *
 * Pensado específicamente para crowdsourcing: con 2 personas reportando la
 * confianza es baja, con 15 es alta — el mapa tiene que mostrar esa
 * diferencia honestamente, no tratar todos los puntos como si fueran
 * igual de ciertos.
 *
 * Recibe un Int plano (0..100), no ReliabilityScore de domain-transport, a
 * propósito: core-ui no debe depender de ningún módulo de dominio (core
 * está por debajo de domain en la jerarquía de capas). Quien llama a esto
 * desde una feature es responsable de pasar `vehicle.positionConfidence?.percent`.
 * `null` significa "no aplica el concepto" (fuente oficial) — se trata como sólido.
 */
enum class VehicleConfidenceStyle(val alpha: Float, val showsDashedRing: Boolean, val label: String?) {
    /** Fuente oficial, o crowdsourcing con muchos reportes coincidentes — se muestra sólido. */
    SOLID(alpha = 1f, showsDashedRing = false, label = null),

    /** Crowdsourcing con evidencia moderada — se atenúa un poco, sin alarmar. */
    MODERATE(alpha = 0.75f, showsDashedRing = false, label = null),

    /** Muy pocos reportes — se muestra claramente como estimado, no como un dato firme. */
    ESTIMATED(alpha = 0.5f, showsDashedRing = true, label = "Estimado");

    companion object {
        private const val HIGH_CONFIDENCE_THRESHOLD = 70
        private const val MODERATE_CONFIDENCE_THRESHOLD = 40

        fun from(confidencePercent: Int?): VehicleConfidenceStyle = when {
            confidencePercent == null -> SOLID // no aplica el concepto (fuente oficial): se confía por defecto
            confidencePercent >= HIGH_CONFIDENCE_THRESHOLD -> SOLID
            confidencePercent >= MODERATE_CONFIDENCE_THRESHOLD -> MODERATE
            else -> ESTIMATED
        }
    }
}
