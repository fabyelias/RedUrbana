package com.redurbana.domain.crowdsourcing

import com.redurbana.domain.transport.model.GeoPoint
import kotlinx.datetime.Instant

/** Una muestra cruda de ubicación, tal como la entrega el GPS del dispositivo. */
data class LocationSample(
    val position: GeoPoint,
    val speedKmh: Float,
    val bearingDegrees: Float,
    val timestamp: Instant,
)

sealed interface OnBusSignal {
    /** No hay evidencia suficiente todavía — no reportar ningún ping. */
    data object Unknown : OnBusSignal
    /** El patrón de movimiento sugiere colectivo: velocidad + paradas intermitentes + persistencia en el tiempo. */
    data class LikelyOnBus(val confidence: Float) : OnBusSignal
    /** Quieto, o a velocidad de caminata/auto particular — no reportar. */
    data object NotOnBus : OnBusSignal
}

/**
 * Decide, a partir de una ventana de [LocationSample] recientes, si el
 * patrón de movimiento es compatible con "va arriba de un colectivo".
 *
 * Puro Kotlin, sin Android ni GPS real — se testea con listas de samples
 * armadas a mano. La implementación real de reporte (LocationReporter, en
 * data-crowdsourcing) es la que alimenta esto con datos del dispositivo.
 *
 * Señales que combina (deliberadamente simples — no es ni pretende ser un
 * clasificador de ML, es un filtro barato para no reportar constantemente
 * ni molestar con pings de gente caminando o en auto):
 *  - Velocidad sostenida en rango de colectivo urbano (6-55 km/h en
 *    promedio, con paradas ocasionales — no autopista a 100, no caminata).
 *  - El movimiento persiste por más de [MIN_DURATION_SECONDS] — descarta
 *    falsos positivos de un semáforo en auto.
 *  - Al menos una parada breve dentro de la ventana (típico de colectivo
 *    parando en semáforos/paradas) sin llegar a "usuario quieto del todo".
 */
object OnBusHeuristic {

    private const val MIN_BUS_SPEED_KMH = 6f
    private const val MAX_BUS_SPEED_KMH = 55f
    private const val MIN_DURATION_SECONDS = 90

    fun evaluate(samples: List<LocationSample>): OnBusSignal {
        if (samples.size < 4) return OnBusSignal.Unknown

        val durationSeconds = (samples.last().timestamp - samples.first().timestamp).inWholeSeconds
        if (durationSeconds < MIN_DURATION_SECONDS) return OnBusSignal.Unknown

        val speeds = samples.map { it.speedKmh }
        val avgSpeed = speeds.average()
        val maxSpeed = speeds.max()
        val hadBriefStop = speeds.any { it < 3f } // compatible con parar en semáforo/parada

        val speedInRange = avgSpeed in MIN_BUS_SPEED_KMH.toDouble()..MAX_BUS_SPEED_KMH.toDouble()
        val notHighwaySpeed = maxSpeed < MAX_BUS_SPEED_KMH + 15f // margen para no descartar por un pico puntual

        return when {
            !speedInRange || !notHighwaySpeed -> OnBusSignal.NotOnBus
            speedInRange && hadBriefStop -> {
                val confidence = confidenceFrom(avgSpeed, hadBriefStop)
                OnBusSignal.LikelyOnBus(confidence)
            }
            else -> OnBusSignal.Unknown // en rango de velocidad pero sin patrón de parada — todavía no se sabe
        }
    }

    private fun confidenceFrom(avgSpeed: Double, hadBriefStop: Boolean): Float {
        val speedScore = 1f - (kotlin.math.abs(avgSpeed - 22.0) / 30.0).toFloat().coerceIn(0f, 1f)
        val stopBonus = if (hadBriefStop) 0.2f else 0f
        return (speedScore + stopBonus).coerceIn(0f, 1f)
    }
}
