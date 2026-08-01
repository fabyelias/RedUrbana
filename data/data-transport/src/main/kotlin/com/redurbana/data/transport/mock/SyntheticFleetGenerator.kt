package com.redurbana.data.transport.mock

import com.redurbana.domain.transport.model.Branch
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehicleId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Genera una flota sintética grande (cientos/miles de vehículos en decenas
 * de líneas) sobre el área metropolitana de Buenos Aires, para poder validar
 * rendimiento, animaciones y clustering ANTES de tener una fuente de datos
 * real. Las 4 líneas "reales" de [MockRouteData] (60/152/59/37) se mantienen
 * intactas para las demos de detalle; esto es un generador aparte, pensado
 * para stress-testing del mapa.
 *
 * No tiene ninguna pretensión de precisión geográfica real: son recorridos
 * radiales/circulares procedurales centrados en CABA, suficientes para que
 * el patrón de movimiento se sienta creíble (varias líneas cruzándose,
 * densidad más alta en el centro).
 */
internal object SyntheticFleetGenerator {

    private val cabaCenter = GeoPoint(-34.6037, -58.3816)
    private const val CITY_RADIUS_DEGREES = 0.10 // ~11km, cubre CABA + primer cordón del GBA

    data class GeneratedFleet(
        val routes: Map<RouteId, RouteDetails>,
        val vehicles: List<SimulatedVehicle>,
    )

    /**
     * @param routeCount cantidad de líneas sintéticas a generar
     * @param vehiclesPerRoute vehículos por línea (constante simplificada;
     *   una fuente real tendría densidad variable por línea/horario)
     */
    fun generate(
        routeCount: Int,
        vehiclesPerRoute: Int,
        seed: Long = 42L,
    ): GeneratedFleet {
        val random = Random(seed)
        val routes = mutableMapOf<RouteId, RouteDetails>()
        val vehicles = mutableListOf<SimulatedVehicle>()

        repeat(routeCount) { routeIndex ->
            val routeId = RouteId("SYN-${routeIndex + 1}")
            val path = generateRadialPath(random, routeIndex, routeCount)
            val speed = (18..38).random(random).toFloat() // km/h, tránsito urbano realista

            routes[routeId] = RouteDetails(
                routeId = routeId,
                shortName = "S${routeIndex + 1}",
                company = syntheticCompanyName(random),
                branches = listOf(Branch("R1", "Recorrido sintético ${routeIndex + 1}", path)),
                colorSeed = routeId.value,
                reliability = ReliabilityScore((75..99).random(random)),
            )

            repeat(vehiclesPerRoute) { vehicleIndex ->
                vehicles += SimulatedVehicle(
                    vehicleId = VehicleId("SYN-${routeIndex + 1}-$vehicleIndex"),
                    routeId = routeId,
                    branchId = "R1",
                    internalNumber = (1000 + routeIndex * 100 + vehicleIndex).toString(),
                    path = path,
                    speedKmh = speed,
                    startFractionAlongPath = random.nextFloat(),
                )
            }
        }

        return GeneratedFleet(routes, vehicles)
    }

    /** Genera un recorrido tipo "radio de rueda": del borde de la ciudad hacia el centro y más allá. */
    private fun generateRadialPath(random: Random, index: Int, total: Int): List<GeoPoint> {
        val angle = (2 * Math.PI * index / total) + random.nextDouble(-0.15, 0.15)
        val points = mutableListOf<GeoPoint>()
        val steps = 6
        for (step in 0..steps) {
            // Va desde el borde exterior (step=0) hasta cruzar el centro (step=steps),
            // con algo de curvatura para que no sea una línea perfectamente recta.
            val fraction = step.toDouble() / steps
            val radius = CITY_RADIUS_DEGREES * (1.0 - fraction * 1.3)
            val wobble = sin(fraction * Math.PI * 2) * 0.01
            points += GeoPoint(
                latitude = cabaCenter.latitude + radius * sin(angle) + wobble,
                longitude = cabaCenter.longitude + radius * cos(angle) + wobble,
            )
        }
        return points
    }

    private fun syntheticCompanyName(random: Random): String {
        val prefixes = listOf("Transportes", "Micro", "Línea", "Expreso", "Empresa")
        val suffixes = listOf("del Sur", "Metropolitana", "Norte", "Capital", "Unidos", "AMBA")
        return "${prefixes.random(random)} ${suffixes.random(random)}"
    }

    private fun IntRange.random(random: Random): Int = random.nextInt(first, last + 1)
}
