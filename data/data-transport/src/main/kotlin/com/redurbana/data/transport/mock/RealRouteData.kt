package com.redurbana.data.transport.mock

import android.content.Context
import com.redurbana.domain.transport.model.Branch
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehicleId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Serializable
private data class RealPointDto(val lat: Double, val lng: Double)

@Serializable
private data class RealBranchDto(val id: String, val name: String, val path: List<RealPointDto>)

@Serializable
private data class RealRouteDto(val routeId: String, val shortName: String, val company: String, val branches: List<RealBranchDto>)

/**
 * Carga `assets/real_routes.json`: geometría real de las 138 líneas de
 * colectivo de jurisdicción nacional (CNRT) del área metropolitana de Buenos
 * Aires, 566 ramales en total (fuente: datos.transporte.gob.ar, dataset KML
 * "Líneas jurisdicción nacional RMBA-CNRT"). Reemplaza a
 * `SyntheticFleetGenerator`: qué línea le sirve a un usuario para llegar a
 * destino se calcula ahora sobre el trazado real de cada ramal, no sobre un
 * radio ficticio alrededor de un centro fijo — así funciona igual esté el
 * usuario en Congreso o en Tigre.
 *
 * Es solo geometría: no hay (todavía) horarios/frecuencias reales (GTFS
 * completo), así que la confiabilidad se deja en un valor neutral fijo en
 * vez de inventar variación que no tenemos forma de sustentar.
 */
@Singleton
class RealRouteData @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class LoadedFleet(
        val routes: Map<RouteId, RouteDetails>,
        val vehicles: List<SimulatedVehicle>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): LoadedFleet = withContext(Dispatchers.IO) {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val routeDtos = json.decodeFromString<List<RealRouteDto>>(text)

        val routes = mutableMapOf<RouteId, RouteDetails>()
        val vehicles = mutableListOf<SimulatedVehicle>()

        for (routeDto in routeDtos) {
            val routeId = RouteId(routeDto.routeId)
            val random = Random(routeId.value.hashCode())

            val branches = routeDto.branches.mapNotNull { branchDto ->
                val path = branchDto.path.map { GeoPoint(it.lat, it.lng) }
                if (path.size < 2) return@mapNotNull null
                Branch(
                    id = branchDto.id,
                    name = branchDto.name,
                    path = path,
                    stops = StopGenerator.generateStops(routeId, branchDto.name, path),
                )
            }
            if (branches.isEmpty()) continue

            routes[routeId] = RouteDetails(
                routeId = routeId,
                shortName = routeDto.shortName,
                company = routeDto.company,
                branches = branches,
                colorSeed = routeId.value,
                reliability = REAL_DATA_RELIABILITY,
            )

            // Un vehículo simulado por ramal real: suficiente para que cada
            // línea tenga movimiento visible sin triplicar el costo de
            // simulación (566 ramales ya son 566 vehículos por sí solos).
            branches.forEach { branch ->
                vehicles += SimulatedVehicle(
                    vehicleId = VehicleId("${routeId.value}-${branch.id}"),
                    routeId = routeId,
                    branchId = branch.id,
                    internalNumber = "${routeId.value}${branch.id}",
                    path = branch.path,
                    speedKmh = SPEED_RANGE_KMH.random(random).toFloat(),
                    startFractionAlongPath = random.nextFloat(),
                )
            }
        }

        LoadedFleet(routes, vehicles)
    }

    private fun IntRange.random(random: Random): Int = random.nextInt(first, last + 1)

    private companion object {
        const val ASSET_NAME = "real_routes.json"
        val REAL_DATA_RELIABILITY = ReliabilityScore(90)
        val SPEED_RANGE_KMH = 18..34
    }
}
